#!/usr/bin/env ruby
# frozen_string_literal: true

# tools/import-symbols.rb
#
# The marks of the screens. tools/symbols.tsv says, line by line, which SF
# Symbol the iOS app uses and which Material Symbol stands for it here.
# This fetches the ones that are not in the working copy yet from Google's
# material-design-icons (Apache 2.0), as vector drawables, and writes
# ui/Symbols.kt -- where each mark is named as the iOS app names it.
require 'open-uri'

Encoding.default_external = Encoding::UTF_8
root = File.expand_path('..', __dir__)
rows = File.readlines(File.join(root, 'tools/symbols.tsv'), chomp: true).reject(&:empty?).map { |line| line.split("\t") }
FILLED = %w[play.fill].freeze

rows.each do |sf, material|
  out = File.join(root, "app/src/main/res/drawable/ic_#{material}.xml")
  unless File.exist?(out)
    variant = FILLED.include?(sf) ? "#{material}_fill1_24px.xml" : "#{material}_24px.xml"
    url = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/#{material}/materialsymbolsoutlined/#{variant}"
    File.write(out, URI.parse(url).open.read)
    puts "fetched #{material}"
  end
  # The file tints itself with an attribute only AppCompat has; here the screen gives a mark its colour.
  xml = File.read(out)
  plain = xml.sub(/\n\s*android:tint="[^"]*"/, '')
  File.write(out, plain) if plain != xml
end

camel = lambda do |sf|
  parts = sf.split('.')
  parts.first + parts.drop(1).map { |part| part.match?(/\A\d/) ? part : part.capitalize }.join
end
body = rows.map { |sf, material| "    /** #{sf} */\n    val #{camel.call(sf)} = R.drawable.ic_#{material}" }.join("\n")
File.write(File.join(root, 'app/src/main/java/app/blogsh/android/ui/Symbols.kt'), <<~KOTLIN)
  package app.blogsh.android.ui

  import app.blogsh.android.R

  /**
   * The marks the screens use. Each is named as the iOS app names it -- an SF
   * Symbol's name, without its dots -- and stands for the Material Symbol
   * nearest to it (tools/symbols.tsv says which). The two apps then read
   * alike line for line, and a mark is changed in one place.
   *
   * Written by tools/import-symbols.rb. Not edited by hand.
   */
  object Symbols {
  #{body}
  }
KOTLIN
puts "#{rows.size} marks"
