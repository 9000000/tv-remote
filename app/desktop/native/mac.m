// What a Cocoa app gives its window and webview does not, for TV Remote's window (built with webview's core by build.sh):
// the menu bar (without an Edit menu Cmd+C / Cmd+V do nothing in text fields) and a window that keeps its size and place.
// The page asks its questions itself (ask() in core.js), so WebKit's alert / confirm / prompt are not needed.
#import <Cocoa/Cocoa.h>
#include "webview/webview.h"

static BOOL vi(void) { return [[NSLocale preferredLanguages].firstObject hasPrefix:@"vi"]; }
static NSString *T(NSString *v, NSString *e) { return vi() ? v : e; }

static NSMenuItem *item(NSMenu *m, NSString *title, SEL action, NSString *key) {
    return [m addItemWithTitle:title action:action keyEquivalent:key];
}

static void menus(void) {
    NSMenu *bar = [NSMenu new];
    NSMenu *app = [NSMenu new];
    item(app, T(@"Ẩn TV Remote", @"Hide TV Remote"), @selector(hide:), @"h");
    [app addItem:[NSMenuItem separatorItem]];
    item(app, T(@"Thoát TV Remote", @"Quit TV Remote"), @selector(performClose:), @"q");   // the window's close: the server stops properly
    NSMenu *edit = [[NSMenu alloc] initWithTitle:T(@"Sửa", @"Edit")];
    item(edit, T(@"Hoàn tác", @"Undo"), @selector(undo:), @"z");
    item(edit, T(@"Làm lại", @"Redo"), @selector(redo:), @"Z");
    [edit addItem:[NSMenuItem separatorItem]];
    item(edit, T(@"Cắt", @"Cut"), @selector(cut:), @"x");
    item(edit, T(@"Sao chép", @"Copy"), @selector(copy:), @"c");
    item(edit, T(@"Dán", @"Paste"), @selector(paste:), @"v");
    item(edit, T(@"Chọn tất cả", @"Select All"), @selector(selectAll:), @"a");
    NSMenu *view = [[NSMenu alloc] initWithTitle:T(@"Xem", @"View")];
    item(view, T(@"Tải lại", @"Reload"), @selector(reload:), @"r");
    NSMenuItem *full = item(view, T(@"Toàn màn hình", @"Enter Full Screen"), @selector(toggleFullScreen:), @"f");
    full.keyEquivalentModifierMask = NSEventModifierFlagCommand | NSEventModifierFlagControl;
    NSMenu *win = [[NSMenu alloc] initWithTitle:T(@"Cửa sổ", @"Window")];
    item(win, T(@"Thu nhỏ", @"Minimize"), @selector(performMiniaturize:), @"m");
    item(win, T(@"Phóng to", @"Zoom"), @selector(performZoom:), @"");
    item(win, T(@"Đóng", @"Close"), @selector(performClose:), @"w");
    for (NSMenu *m in @[app, edit, view, win]) [bar addItemWithTitle:@"" action:nil keyEquivalent:@""].submenu = m;
    NSApp.mainMenu = bar;
    NSApp.windowsMenu = win;
}

void tvr_mac_setup(webview_t w) {
    NSWindow *win = (__bridge NSWindow *)webview_get_window(w);
    menus();
    win.minSize = NSMakeSize(360, 480);
    [win center];
    win.frameAutosaveName = @"TV Remote";                             // the size and place it had last time
}
