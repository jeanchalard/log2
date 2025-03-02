#!/usr/bin/ruby -w

require 'fileutils'

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

DEBUG = arg("-d", false)
OUT = arg('-o', true) || '-'
FileUtils.mkdir_p(File.dirname(OUT))
SRC = arg('-i', true)
raise "No input file given" if (SRC.nil?)
SRCFILE = File.new(SRC, File::RDONLY)
OUTFILE = if OUT == '-' then $stdout else File.new(OUT, File::CREAT | File::TRUNC | File::WRONLY) end

def filter(activity)
  case activity
  when /WF. .*/ then return activity
  when /Zzz.*/ then return "Zzz"
  else return "Not work"
  end
end


start = nil
activity = nil
while l = SRCFILE.gets
  case l
  when /\d\d-\d\d/
    OUTFILE.puts "#{start} #{activity}" unless start.nil?
    OUTFILE.puts l
    start = nil
    activity = nil
  when /(\d\d\d\d) (.*)/
    s = $1
    a = filter($2)
    if activity != a
      OUTFILE.puts "#{start} #{activity}" unless start.nil?
      start = s
      activity = a
    end
  end
end
OUTFILE.puts "#{start} #{activity}" unless start.nil?
OUTFILE.close
