require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "QrCameraPro"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]
  s.source          = { :git => package["repository"]["url"], :tag => "#{s.version}" }


  s.source_files = "ios/*.{h,m,mm,swift}" # Explicitly include common native file extensions
  s.ios.deployment_target = '13.0'

  install_modules_dependencies(s)
end
