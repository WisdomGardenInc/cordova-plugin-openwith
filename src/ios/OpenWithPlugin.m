#import <Cordova/CDV.h>
#import "ShareViewController.h"
#import <MobileCoreServices/MobileCoreServices.h>

/*
 * Constants
 */

#define VERBOSITY_DEBUG  0
#define VERBOSITY_INFO  10
#define VERBOSITY_WARN  20
#define VERBOSITY_ERROR 30

/*
 * State variables
 */

static NSDictionary* launchOptions = nil;

/*
 * OpenWithPlugin definition
 */

@interface OpenWithPlugin : CDVPlugin {
    NSString* _loggerCallback;
    NSString* _handlerCallback;
    NSUserDefaults *_userDefaults;
    int _verbosityLevel;
    NSString *_backURL;
}

@property (nonatomic) int verbosityLevel;
@property (nonatomic,retain) NSUserDefaults *userDefaults;
@property (nonatomic,retain) NSString *backURL;
@end

/*
 * OpenWithPlugin implementation
 */

@implementation OpenWithPlugin

@synthesize verbosityLevel = _verbosityLevel;
@synthesize userDefaults = _userDefaults;
@synthesize backURL = _backURL;

//
// Retrieve launchOptions
//
// The plugin mechanism doesn’t provide an easy mechanism to capture the
// launchOptions which are passed to the AppDelegate’s didFinishLaunching: method.
//
// Therefore we added an observer for the
// UIApplicationDidFinishLaunchingNotification notification when the class is loaded.
//
// Source: https://www.plotprojects.com/blog/developing-a-cordova-phonegap-plugin-for-ios/
//
+ (void) load {
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(didFinishLaunching:)
                                               name:UIApplicationDidFinishLaunchingNotification
                                             object:nil];
}

+ (void) didFinishLaunching:(NSNotification*)notification {
    launchOptions = notification.userInfo;
}

- (void) log:(int)level message:(NSString*)message {
    if (level >= self.verbosityLevel) {
        NSLog(@"[OpenWithPlugin.m]%@", message);
    }
}
- (void) debug:(NSString*)message { [self log:VERBOSITY_DEBUG message:message]; }
- (void) info:(NSString*)message { [self log:VERBOSITY_INFO message:message]; }
- (void) warn:(NSString*)message { [self log:VERBOSITY_WARN message:message]; }
- (void) error:(NSString*)message { [self log:VERBOSITY_ERROR message:message]; }


- (void) fetchSharedData :(CDVInvokedUrlCommand*)command {
    [self debug:@"[fetchSharedData]"];
    CDVPluginResult* pluginResult = [self getSharedData];
    if(pluginResult != nil) {
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } else {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
    }
}

- (void) setVerbosity:(CDVInvokedUrlCommand*)command {
    NSNumber *value = [command argumentAtIndex:0
                                   withDefault:[NSNumber numberWithInt: VERBOSITY_INFO]
                                      andClass:[NSNumber class]];
    self.verbosityLevel = value.integerValue;
    [self.userDefaults setInteger:self.verbosityLevel forKey:@"verbosityLevel"];
    [self.userDefaults synchronize];
    [self debug:[NSString stringWithFormat:@"[setVerbosity] %d", self.verbosityLevel]];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(CDVPluginResult*) getSharedData {
    [self.userDefaults synchronize];
    NSObject *object = [self.userDefaults objectForKey:@"shared"];
    if (object == nil) {
        [self debug:@"[getSharedData] Nothing to share"];
        return nil;
    }

    // Clean-up the object, assume it's been handled from now, prevent double processing
    [self.userDefaults removeObjectForKey:@"shared"];

    // Extract sharing data, make sure that it is valid
    if (![object isKindOfClass:[NSDictionary class]]) {
        [self debug:@"[getSharedData] Data object is invalid"];
        return nil;
    }
    NSDictionary *dict = (NSDictionary*)object;

    NSString *text = dict[@"text"];
    NSArray *items = dict[@"items"];
    self.backURL = dict[@"backURL"];

    NSArray *processedItems = [self processSharedItems:items];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:@{
        @"text": text,
        @"items": processedItems,
        @"receivedCounts": dict[@"receivedCounts"],
        @"maxAttachmentCount": dict[@"maxAttachmentCount"]
    }];
    
    return pluginResult;
}

- (NSMutableArray*) processSharedItems:(NSArray*)items {
    NSMutableArray *processedItems = [[NSMutableArray alloc] init];
    for (NSDictionary *item in items) {
        NSMutableDictionary *processedItem = [NSMutableDictionary dictionaryWithDictionary:item];
        NSData *content = item[@"data"];
        NSString *fileName = item[@"name"];
        if ([item[@"data"] isKindOfClass:[NSData class]]) {
            // If shared data, save it to the temp directory and return the saved file path to cordova.
            NSURL *tempDirURL = [NSURL fileURLWithPath:NSTemporaryDirectory() isDirectory:YES];
            NSURL *fileURL = [tempDirURL URLByAppendingPathComponent:fileName];
            [content writeToFile:[fileURL path] atomically:YES];
            NSLog(@"Saved file URL: %@", [fileURL path]);
            [processedItem setValue:fileURL.absoluteString forKeyPath:@"data"];
        }
        [processedItems addObject:processedItem];
    }
    return processedItems;
}

- (void) _init {
    self.userDefaults = [[NSUserDefaults alloc] initWithSuiteName:SHAREEXT_GROUP_IDENTIFIER];
    self.verbosityLevel = VERBOSITY_INFO;
}

- (void) init:(CDVInvokedUrlCommand*)command  {
    [self info:@"[init]"];
    [self _init];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

// Exit after sharing
- (void) exit:(CDVInvokedUrlCommand*)command {
    [self debug:[NSString stringWithFormat:@"[exit] %@", self.backURL]];
    if (self.backURL != nil) {
        UIApplication *app = [UIApplication sharedApplication];
        NSURL *url = [NSURL URLWithString:self.backURL];
        if ([app canOpenURL:url]) {
            [app openURL:url];
        }
    }
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}


@end
