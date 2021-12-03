var fs = require('fs');
var path = require('path');

// Determine the full path to the ios platform
function iosFolder(context) {
  return context.opts.cordova.project
    ? context.opts.cordova.project.root
    : path.join(context.opts.projectRoot, 'platforms/ios/');
}

// Determine the full path to the app's xcode project file.
function findXCodeproject(context, callback) {
  fs.readdir(iosFolder(context), function (err, data) {
    var projectFolder
    var projectName
    // Find the project folder by looking for *.xcodeproj
    if (data && data.length) {
      data.forEach(function (folder) {
        if (folder.match(/\.xcodeproj$/)) {
          projectFolder = path.join(iosFolder(context), folder)
          projectName = path.basename(folder, '.xcodeproj')
        }
      })
    }

    if (!projectFolder || !projectName) {
      throw redError('Could not find an .xcodeproj folder in: ' + iosFolder(context))
    }

    if (err) {
      throw redError(err)
    }

    callback(projectFolder, projectName)
  })
}

function parsePbxProject(context, pbxProjectPath) {
  var xcode = require('xcode');
  console.log('    Parsing existing project at location: ' + pbxProjectPath + '...');
  var pbxProject;
  if (context.opts.cordova.project) {
    pbxProject = context.opts.cordova.project.parseProjectFile(context.opts.projectRoot).xcode;
  } else {
    pbxProject = xcode.project(pbxProjectPath);
    pbxProject.parseSync();
  }
  return pbxProject;
}

module.exports = function (context) {
  var Q = require('q');
  var deferral = new Q.defer();

  var profileId = (() => {
    var arg = process.argv.find(a => a.startsWith('--extensionProvisioningProfile'))
    if (arg && arg.split('=').length > 1) {
      return arg.split('=')[1]
    }
  })()
  if (profileId) {
    findXCodeproject(context, function (projectFolder) {
      var pbxProjectPath = path.join(projectFolder, 'project.pbxproj')
      var pbxProject = parsePbxProject(context, pbxProjectPath)
      var configurations = pbxProject.pbxXCBuildConfigurationSection()
      for (var key in configurations) {
        if (typeof configurations[key].buildSettings !== 'undefined') {
          var buildSettingsObj = configurations[key].buildSettings
          if (typeof buildSettingsObj['PRODUCT_NAME'] !== 'undefined') {
            var productName = buildSettingsObj['PRODUCT_NAME']
            if (productName.indexOf('ShareExt') >= 0) {
              buildSettingsObj['PROVISIONING_PROFILE'] = profileId
              console.log('Added fastlane provisioning profile to share extension')
            }
          }
        }
      }
      fs.writeFileSync(pbxProjectPath, pbxProject.writeSync());
      console.log('Written to share extension ');

      deferral.resolve();
    })
  } else {
    deferral.resolve();
  }
  return deferral.promise;
}
