#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html
#
Pod::Spec.new do |s|
  s.name             = 'image_picker_saver'
  s.version          = '0.0.1'
  s.summary          = 'Flutter plugin that shows an image picker and save image to photo library.'
  s.description      = <<-DESC
Flutter plugin that shows an image picker.
                       DESC
  s.homepage         = 'https://github.com/cnhefang/image_picker_saver'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Aaron Ho' => 'cnhefang@outlook.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.public_header_files = 'Classes/**/*.h'
  s.dependency 'Flutter'
end
