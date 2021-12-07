const PLUGIN_ID = 'cc.fovea.cordova.openwith';
const BUNDLE_SUFFIX = '.shareextension';

var fs = require('fs')
var path = require('path')
var packageJson;

// Determine the full path to the ios platform
function iosFolder(context) {
  return context.opts.cordova.project
    ? context.opts.cordova.project.root
    : path.join(context.opts.projectRoot, 'platforms/ios/')
}

function getBundleId(context, configXml) {
  var elementTree = require('elementtree');
  var etree = elementTree.parse(configXml);
  return etree.getroot().get('id');
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
  var xcode = require('xcode')
  console.log('    Parsing existing project at location: ' + pbxProjectPath + '...')
  var pbxProject
  if (context.opts.cordova.project) {
    pbxProject = context.opts.cordova.project.parseProjectFile(context.opts.projectRoot).xcode
  } else {
    pbxProject = xcode.project(pbxProjectPath)
    pbxProject.parseSync()
  }
  return pbxProject
}

function getPreferenceValue(configXml, name) {
  var value = configXml.match(new RegExp('name="' + name + '" value="(.*?)"', 'i'))
  if (value && value[1]) {
    return value[1]
  } else {
    return null
  }
}

function getCordovaParameter(configXml, variableName) {
  var variable = packageJson.cordova.plugins[PLUGIN_ID][variableName]
  if (!variable) {
    variable = getPreferenceValue(configXml, variableName)
  }
  return variable
}

module.exports = function (context) {
  var Q = require('q')
  var deferral = new Q.defer()

  packageJson = require(path.join(context.opts.projectRoot, 'package.json'));

  var configXml = fs.readFileSync(path.join(context.opts.projectRoot, 'config.xml'), 'utf-8')
  if (configXml) {
    configXml = configXml.substring(configXml.indexOf('<'))
  }

  var bundleId = getBundleId(context, configXml)
  var extensionBundle = bundleId + BUNDLE_SUFFIX

  var buildJsonPath = path.join(context.opts.projectRoot, 'build.json')
  var buildJson = null
  if(fs.existsSync(buildJsonPath)) {
    buildJson = require(buildJsonPath)
  }
  if (buildJson) {
    var devTeam = getCordovaParameter(configXml, 'SHAREEXT_DEVELOPMENT_TEAM')
    if(devTeam) {
      devTeam = buildJson && buildJson.ios && buildJson.ios.debug && buildJson.ios.debug.developmentTeam
    }
    findXCodeproject(context, function (projectFolder) {
      var pbxProjectPath = path.join(projectFolder, 'project.pbxproj')
      var pbxProject = parsePbxProject(context, pbxProjectPath)
      var configurations = pbxProject.pbxXCBuildConfigurationSection()
      for (var key in configurations) {
        if (typeof configurations[key].buildSettings !== 'undefined') {
          const mode = configurations[key].name
          var buildSettingsObj = configurations[key].buildSettings
          if (typeof buildSettingsObj['PRODUCT_NAME'] !== 'undefined') {
            var productName = buildSettingsObj['PRODUCT_NAME']
            if (productName.indexOf('ShareExt') >= 0) {
              var buildMode = mode.indexOf('Debug') >= 0 ? 'debug' : 'release'
              var profileId = buildJson && buildJson.ios && buildJson.ios[buildMode] && buildJson.ios[buildMode].provisioningProfile && buildJson.ios[buildMode].provisioningProfile[extensionBundle]
              if(profileId) {
                buildSettingsObj['PROVISIONING_PROFILE'] = profileId
              }
              if (devTeam) {
                buildSettingsObj['DEVELOPMENT_TEAM'] = devTeam
              }
              buildSettingsObj['CODE_SIGN_STYLE'] = 'Manual'
              buildSettingsObj['CODE_SIGN_IDENTITY'] = buildMode === 'release' ? '"iPhone Distribution"' : '"iPhone Developer"'
              console.log('Added fastlane provisioning profile: ' + profileId + ', dev team: ' + devTeam + ', for mode: ' + buildMode)
            }
          }
        }
      }
      fs.writeFileSync(pbxProjectPath, pbxProject.writeSync())
      console.log('Written to share extension ')

      deferral.resolve()
    })
  } else {
    deferral.resolve()
  }
  return deferral.promise
}
