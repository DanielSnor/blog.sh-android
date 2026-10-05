#!/usr/bin/env ruby
# frozen_string_literal: true

# tools/import-strings.rb <path to blog.sh-ios>
#
# The app's words come from the iOS app's string catalog, so the two apps
# say the same thing in every language the catalog has. This reads
# blogsh/Localizable.xcstrings (and the Swift sources, for the English of
# the keys that are names rather than sentences) and writes
#
#   app/src/main/res/values/strings.xml       English
#   app/src/main/res/values-<lang>/strings.xml  every other language
#   tools/strings-map.tsv                     resource name <TAB> the iOS key
#
# A resource is named after its English: the first words, folded. The
# name of a string therefore changes when its English does, and the
# compiler says where it was used.
require 'json'

Encoding.default_external = Encoding::UTF_8
ios = ARGV[0] or abort('usage: import-strings.rb <path to blog.sh-ios>')
root = File.expand_path('..', __dir__)
catalog = JSON.parse(File.read(File.join(ios, 'blogsh/Localizable.xcstrings'), encoding: 'UTF-8'))
sources = Dir[File.join(ios, 'blogsh/**/*.swift')].map { |f| File.read(f, encoding: 'UTF-8') }.join("\n")

# The English of a key that is a name: its defaultValue in the code.
defaults = {}
sources.scan(/"([a-z]+(?:\.[a-z_0-9]+)+)",\s*defaultValue:\s*"((?:[^"\\]|\\.)*)"/) do |key, value|
  # What Swift puts into a sentence -- \(name) -- is an argument of it here.
  value = value.gsub(/\\\((?:[^()]|\([^()]*\))*\)/, '%@')
  defaults[key] = value.gsub(/\\(["\\])/, '\1').gsub('\n', "\n")
end

RESERVED = %w[if else for while do when is in as try catch class object package return true false null this super
              val var fun interface typealias throw break continue new default switch case void int long public private].freeze

def fold(text)
  text.unicode_normalize(:nfd).gsub(/\p{M}+/, '').downcase
end

def name_for(key, taken)
  base = if key.match?(/\A[a-z]+(\.[a-z_0-9]+)+\z/)
           key.tr('.', '_')
         else
           words = fold(key).gsub(/%(\d+\$)?(@|l{0,2}[dfiu]|\.\d+f)/, ' ').scan(/[a-z0-9]+/)
           words.first(7).join('_')[0, 56].sub(/_+\z/, '')
         end
  base = "s_#{base}" if base.empty? || base.match?(/\A\d/) || RESERVED.include?(base)
  name = base
  n = 2
  while taken.include?(name)
    name = "#{base}_#{n}"
    n += 1
  end
  taken << name
  name
end

# %@ and %lld are Apple's; Android counts its arguments.
def placeholders(text)
  index = 0
  text.gsub(/%(?:(\d+)\$)?(@|l{0,2}[dfiu]|\.\d+f)/) do
    position = Regexp.last_match(1)
    kind = Regexp.last_match(2)
    index += 1
    conversion = case kind
                 when '@' then 's'
                 when /\.\d+f/, /f\z/ then kind.delete('l')
                 else 'd'
                 end
    "%#{position || index}$#{conversion}"
  end
end

def escape(text)
  out = placeholders(text)
  # A per cent that is not a placeholder has to be doubled once there is one.
  out = out.gsub(/%(?!\d+\$[sd]|\d+\$\.\d+f|%)/, '%%') if out.match?(/%\d+\$/)
  out = out.gsub('\\') { '\\\\' }.gsub("'") { "\\'" }.gsub('"') { '\\"' }.gsub("\n") { '\\n' }
  out = out.gsub('&', '&amp;').gsub('<', '&lt;').gsub('>', '&gt;')
  out = "\\#{out}" if out.start_with?('@', '?')
  out = "\"#{out}\"" if out.match?(/\A\s|\s\z|  /)
  out
end

taken = []
rows = []
languages = Hash.new { |h, k| h[k] = {} }
catalog['strings'].keys.sort.each do |key|
  next if key.strip.empty?

  entry = catalog['strings'][key]
  english = entry.dig('localizations', 'en', 'stringUnit', 'value') || defaults[key] || key
  name = name_for(key, taken)
  rows << [name, key, english]
  (entry['localizations'] || {}).each do |lang, value|
    next if lang == 'en'

    text = value.dig('stringUnit', 'value')
    languages[lang][name] = text if text && !text.empty?
  end
end

def write(path, pairs)
  FileUtils.mkdir_p(File.dirname(path))
  body = pairs.map { |name, text| "    <string name=\"#{name}\">#{escape(text)}</string>" }.join("\n")
  File.write(path, <<~XML)
    <?xml version="1.0" encoding="utf-8"?>
    <!-- Written by tools/import-strings.rb from the iOS app's string catalog. Not edited by hand. -->
    <resources>
    #{body}
    </resources>
  XML
end

require 'fileutils'
write(File.join(root, 'app/src/main/res/values/strings.xml'), rows.map { |name, _, english| [name, english] })
languages.each do |lang, texts|
  write(File.join(root, "app/src/main/res/values-#{lang}/strings.xml"), rows.filter_map { |name, _, _| [name, texts[name]] if texts[name] })
end
File.write(File.join(root, 'tools/strings-map.tsv'), rows.map { |name, key, _| "#{name}\t#{key.gsub("\n", '\n')}" }.join("\n") + "\n")
puts "#{rows.size} strings, languages: #{(['en'] + languages.keys).join(', ')}"
