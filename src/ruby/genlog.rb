#!/usr/bin/ruby -w

require 'fileutils'

RESET_TIME = "!!!!" # Time at which the day starts
ZZZ = "Zzz" # What you're doing at the start of a month

# Returns the argument of the passed switch, or null if the switch is not present
def arg(arg, takesArg)
  if ARGV.include?(arg)
    i = ARGV.index(arg)
    ARGV.delete_at(i)
    return true if !takesArg
    ARGV.delete_at(i)
  else
    nil
  end
end

def parsePeriod(period, defaultYear)
  def parseAdhocDate(date, defaultYear)
    adhocDate = Struct.new(:date, :yearWasExplicit)
    m = date.match(/(\d\d\d\d)?(\d\d)(\d\d)/)
    return adhocDate.new(Time.local(if m[1].nil? then defaultYear else m[1].to_i end, m[2].to_i, m[3].to_i, 0, 0, 0), !m[1].nil?)
  end
  if (period.match?(/^(\d\d\d\d-)?(\d\d)-?(\d\d)$/))
    period = period + "~" + period
  end
  if (!period.include?('~')) then raise "Period must include ~ : ~06-12, 06-03~, or 06-03~06-12 (dashes optional)" end
  period = period.delete('-')
  period = '0101' + period if period[0] == '~'
  period = period + '1231' if period[-1] == '~'
  period = period.split('~')
  from = parseAdhocDate(period[0], defaultYear)
  to = parseAdhocDate(period[1], defaultYear)
  if (to.date < from.date)
    if (!from.yearWasExplicit && to.yearWasExplicit)
      from.date = Time.local(to.date.year - 1, from.date.month, from.date.day)
    elsif (!to.yearWasExplicit)
      # If neither were explicit (or from was) then take the default year for 'from' and move the 'to' one year forward
      to.date = Time.local(from.date.year + 1, to.date.month, to.date.day)
    end
  end
  to.date += 24 * 3600
  $stderr.puts "Selected period : #{from.date} ~ #{to.date}"
  return [from.date, to.date]
end

def readRules(rules)
  s = ""
  mode = nil
  dir = File.dirname(rules)
  File.readlines(rules).each do |l|
    case l
    when /^\s*#/, /^$/ then # nothing, it's a comment or an empty line
    when /^\[collapse(\/i)?\]$/i
      mode = 'rules'
      s += "[rules#{$1}]\n"
    when /^\[([^\]\/]+)(\/i)?\]$/i
      mode = $1
      s += l
    else
      case mode
      when /general/i
        case l
        when /include (.+)/
          s += readRules("#{dir}/#{$1}")
        when /name\s+=\s+(.+)/ then s += l
        when /mode\s+=\s+(.+)/ then # nothing
        end
      else
        s += l
      end
    end
  end
  s
end

def deduceYear(f) # File
  m = File.absolute_path(f).match(/\D(20\d\d)(?!.*20\d\d)/) # last occurrence of 20\d\d in the absolute path
  if m.nil? then Time.now.year else m[1].to_i end
end

def readData(data)
  result = []
  data.each do |file|
    year = deduceYear(file)
    $stderr.puts "Reading #{file}"
    prevDate = nil
    prevAct = nil
    date = nil
    File.readlines(file).each do |l|
      l = l.chomp.gsub(/\s*#.*/, '')
      next if l.empty?
      if l.match(/^(\d\d-\d\d)/)
        date = "#{year}-#{$1}"
        if prevDate.nil?
          date.match(/(\d\d\d\d)-(\d\d)-(\d\d)/)
          d = Time.gm($1.to_i, $2.to_i, $3.to_i) - 86400
          prevDate = "#{d.year}-#{d.month}-#{d.day}:#{RESET_TIME}"
        end
        d = "#{date}:#{RESET_TIME}"
        if prevAct.nil?
          prevAct = ZZZ
        else
          result << "#{prevDate}:#{d} #{prevAct}"
        end
        prevDate = d
      elsif l.match(/^(\d\d\d\d) (.*)/)
        time = $1
        act = $2
        d = "#{date}:#{time}"
        raise "Not ordered #{prevDate} <> #{d}" if (prevDate >= d)
        result << "#{prevDate}:#{d} #{prevAct}" unless prevAct.nil?
        prevAct = act
        prevDate = d
      elsif l.match(/^\d\d\d\d\s*$/)
        # Nothing – this is an end time written temporarily in a file to remember when something ended, but
        # the next activity isn't known yet.
      else
        raise "Unable to parse data : #{l}"
      end
    end
    result << "#{prevDate}:#{date}:#{RESET_TIME} #{prevAct}"
  end
  data = collapseIdentical(result.sort)
  data[0].sub!(RESET_TIME, '0000')
  data[-1].sub!(RESET_TIME, '2400')
  data.join("\n")
end

def collapseIdentical(data)
  result = []
  prevAct = nil
  prevDate = nil
  prevTime = nil
  prevl = nil
  data.each do |l|
    l.match(/(\d\d\d\d-\d\d-\d\d):(....):(\d\d\d\d-\d\d-\d\d):(....) (.*)/)
    if (prevAct != $5)
      prevDate = $1
      prevTime = $2
      prevAct = $5
      result << prevl unless prevl.nil?
      prevl = l
    else
      date = $3
      time = $4.to_i
      if prevDate != date
        date = prevDate
        time += 2400
      end
      time = "%04d" % time
      prevl = "#{prevDate}:#{prevTime}:#{date}:#{time} #{$5}"
    end
  end
  result << prevl
end

DEBUG = arg("-d", false)
PERIOD = parsePeriod(arg("-p", true) || '2000-01-01~2200-12-31', Time.now.year)
OUTDIR = arg('-o', true) || 'default'
FileUtils.mkdir_p(OUTDIR)
RULES = arg("-r", true) || "rules/default.grc"
SRCDIR = arg('-s', true) || '.'
#DATA = Dir["#{SRCDIR}/data*/*"]
DATA = Dir["#{SRCDIR}/data/2023_08*"]

rules = readRules(RULES).gsub("\\", "\\\\\\").gsub("\"", "\\\"").gsub("\n", "\\n")
data = readData(DATA).gsub("\\", "\\\\\\").gsub("\"", "\\\"").gsub("\n", "\\n")

File.write("#{OUTDIR}/data.js", "data = \"#{data}\"\nrules = \"#{rules}\"")

# data.split("\\n").each do |l|
#   puts l
# end