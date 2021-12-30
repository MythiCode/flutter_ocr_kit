#import "OcrkitPlugin.h"
#if __has_include(<ocrkit/ocrkit-Swift.h>)
#import <ocrkit/ocrkit-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "ocrkit-Swift.h"
#endif

@implementation OcrkitPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftOcrkitPlugin registerWithRegistrar:registrar];
}
@end
