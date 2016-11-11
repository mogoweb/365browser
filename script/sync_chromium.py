#!/usr/bin/env python
#
# Copyright (c) 2015 The mogoweb project. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import optparse
import os
import shutil
import sys

import constants
import resource_util
import zipfile

sys.path.append(os.path.join(os.path.dirname(__file__), "dirsync-2.1"))
from dirsync import sync

def sync_java_files(options):
    app_java_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "java")
    chrome_java_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "src")
    args = {'exclude': ['\S+\\.aidl']}
    sync(chrome_java_dir, app_java_dir, "sync", **args)

    chrome_java_dir = os.path.join(options.chromium_root, "chrome/android/webapk/libs/client/src")
    sync(chrome_java_dir, app_java_dir, "sync")

    chrome_java_dir = os.path.join(options.chromium_root, "chrome/android/webapk/libs/common/src")
    sync(chrome_java_dir, app_java_dir, "sync")

    chrome_java_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                   "gen/chrome/android/webapk/libs/client/runtime_library_version_java/java_cpp_template/org/chromium/webapk/lib/client")
    sync(chrome_java_dir, app_java_dir, "sync")

    # sync aidl files
    app_aidl_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "aidl")
    args = {'only': ['\S+\\.aidl'], 'ignore': ['\S*common.aidl']}
    sync(chrome_java_dir, app_aidl_dir, "sync", **args)

    # sync aidl file for webapk
    webapk_aidl_dir = os.path.join(options.chromium_root, "chrome/android/webapk/libs/runtime_library/src")
    sync(webapk_aidl_dir, app_aidl_dir, "sync", **args)

    # sync generated enums files
    #gen_enums_dir = os.path.join(options.chromium_root, "out", options.buildtype,
    #                             "gen", "enums")
    #for dir in os.listdir(gen_enums_dir):
    #    java_dir = os.path.join(gen_enums_dir, dir)
    #    args = {'exclude':['org/chromium/(android_webview|base|blink_public|content|content_public|media|net|sync|ui)\S*',
    #                       'org/chromium/components/(dom_distiller|bookmarks)S*']}
    #    sync(java_dir, app_java_dir, "sync", **args)

    # sync generated template files
    gen_template_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                 "gen", "templates")
    for dir in os.listdir(gen_template_dir):
        java_dir = os.path.join(gen_template_dir, dir)
        args = {'exclude':['org/chromium/(android_webview|base|blink_public|content|content_public|media|net|sync|ui)\S*',
                           'org/chromium/components/(dom_distiller|bookmarks)S*']}
        sync(java_dir, app_java_dir, "sync", **args)

    # sync NativeLibraries.java
    native_libraries_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                        "gen", "chrome", "android", "swe_browser_apk_common__native_libraries_java",
                                        "java_cpp_template")
    sync(native_libraries_dir, app_java_dir, "sync")

    # sync BuildConfig.java
    build_config_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                    "gen", "base/base_build_config_gen/java_cpp_template")
    sync(build_config_dir, app_java_dir, "sync")

    # unzip generated srcjar files
    srcjars = ["chrome/android/document_tab_model_info_proto_java__protoc_java.srcjar",
               "chrome/android/chrome_android_java_enums_srcjar.srcjar",
               "chrome/android/chrome_android_java_google_api_keys_srcjar.srcjar",
               "chrome/android/resource_id_javagen.srcjar",
               "chrome/content_setting_javagen.srcjar",
               "chrome/content_settings_type_javagen.srcjar",
               "chrome/data_use_ui_message_enum_javagen.srcjar",
               "chrome/signin_metrics_enum_javagen.srcjar",
               "chrome/website_settings_action_javagen.srcjar",
               "components/browsing_data/core/browsing_data_utils_java.srcjar",
               "components/infobars/core/infobar_enums_java.srcjar",
               "components/ntp_snippets/ntp_snippets_java_enums_srcjar.srcjar",
               "components/ntp_tiles/ntp_tiles_enums_java.srcjar",
               "components/offline_pages/offline_page_model_enums_java.srcjar",
               "components/omnibox/browser/autocomplete_match_javagen.srcjar",
               "components/omnibox/browser/autocomplete_match_type_javagen.srcjar",
               "components/security_state/security_state_enums_java.srcjar",
               "third_party/WebKit/public/blink_headers_java_enums_srcjar.srcjar",
               "third_party/WebKit/public/platform/modules/payments/payment_request.mojom.srcjar",
               "third_party/WebKit/public/platform/modules/webshare/webshare.mojom.srcjar"]
    for srcjar in srcjars:
        zip_ref = zipfile.ZipFile(os.path.join(options.chromium_root, "out", options.buildtype,
                                               "gen", srcjar), 'r')
        zip_ref.extractall(app_java_dir)
        zip_ref.close()

def sync_so_files(options):
    app_lib_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "jniLibs", "armeabi-v7a")
    chrome_so_lib_dir = os.path.join(options.chromium_root, "out", options.buildtype)
    args = {'only':['libicuuc\\.cr\\.so$', 'libc\\+\\+_shared\\.so$', 'libicui18n\\.cr\\.so$',
                    'libswe\\.so$', 'libswecore\\.so$', 'libswewebrefiner\\.so$', 'libswev8\\.so$'],
            'ignore': []}
    sync(chrome_so_lib_dir, app_lib_dir, "sync", **args)

def sync_jar_files(options):
    app_lib_dir = os.path.join(constants.DIR_APP_ROOT, "libs")
    java_modules = ["base", "blimp/client/public", "blimp/client/core",
                    "components/bookmarks/common/android", "components/dom_distiller/android",
                    "components/gcm_driver/android", "components/gcm_driver/instance_id/android",
                    "components/invalidation/impl",
                    "components/location/android",
                    "components/navigation_interception/android", "components/policy/android",
                    'components/precache/android',
                    "components/safe_json/android", "components/sync/android",
                    "components/url_formatter/android", "components/variations/android", "components/web_contents_delegate_android",
                    "components/web_refiner", "components/web_restrictions",
                    "content/public/android", "device/battery/android", "device/geolocation", "device/vibration/android",
                    "media/base/android", "media/capture/content/android", "media/capture/video/android", "media/midi",
                    "mojo/android", "mojo/public/java",
                    "net/android", "printing",
                    "third_party/android_data_chart",
                    "third_party/android_media",
                    "third_party/android_protobuf", "third_party/android_swipe_refresh",
                    "third_party/cacheinvalidation",
                    "third_party/gif_player", "third_party/leakcanary", "third_party/jsr-305",
                    "ui/android"]
    args = {'only': ['\w+_java\\.jar$', '\w+_\w+_java\\.jar$', '\w+_\w+_\w+_java\\.jar$', 'bindings\\.jar$', 'system\\.jar$',
                    'cacheinvalidation_javalib\\.jar$', 'jsr_305_javalib\\.jar$', 'java\\.jar$',
                    'protobuf_nano_javalib\\.jar$', 'web_contents_delegate_android_java\\.jar$',
                    'vibration_manager_android\\.jar$'],
            'ignore': ['chrome_java\\.jar$']}
    for mod in java_modules:
        chrome_java_lib_dir = os.path.join(options.chromium_root, "out", options.buildtype, "lib.java", mod)
        sync(chrome_java_lib_dir, app_lib_dir, "sync", **args)

    # sync device jar
    device_modules = ["bluetooth", "gamepad", "power_save_blocker", "usb"]
    for mod in device_modules:
        shutil.copy(os.path.join(options.chromium_root, "out", options.buildtype, "lib.java/device", mod, "java.jar"),
                    os.path.join(app_lib_dir, mod + "_java.jar"))
    shutil.copy(os.path.join(options.chromium_root, "out", options.buildtype, "lib.java/device/nfc/android/java.jar"),
                os.path.join(app_lib_dir, "nfc_java.jar"))

    # sync other java.jar files
    shutil.copy(os.path.join(options.chromium_root, "out", options.buildtype, "lib.java/components/signin/core/browser/android/java.jar"),
                os.path.join(app_lib_dir, "signin_java.jar"))
    shutil.copy(os.path.join(options.chromium_root, "out", options.buildtype, "lib.java/components/spellcheck/browser/android/java.jar"),
                os.path.join(app_lib_dir, "spellcheck_java.jar"))
    shutil.copy(os.path.join(options.chromium_root, "out", options.buildtype, "lib.java/device/vibration/mojo_bindings_java.jar"),
                os.path.join(app_lib_dir, "vibration_mojo_bindings_java.jar"))
    shutil.copy(os.path.join(options.chromium_root, "out", options.buildtype, "lib.java/device/nfc/mojo_bindings_java.jar"),
                os.path.join(app_lib_dir, "nfc_mojo_bindings_java.jar"))

    # sync generated jars
    #java_modules = ["base"]
    #args = {'only':['base_java__compile_java\\.javac\\.jar$'],
    #        'ignore': []}
    #for mod in java_modules:
    #    chrome_java_lib_dir = os.path.join(options.chromium_root, "out", options.buildtype, "gen", mod)
    #    sync(chrome_java_lib_dir, app_lib_dir, "sync", **args)

def sync_chromium_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "chrome_res", "src", "main", "res")
    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res")
    sync(chrome_res_dir, library_res_dir, "sync")

    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res_chromium")
    sync(chrome_res_dir, library_res_dir, "sync")

    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res_template")
    sync(chrome_res_dir, library_res_dir, "sync")

    # sync chrome generated string resources
    chrome_gen_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome", "java", "res")
    sync(chrome_gen_res_dir, library_res_dir, "sync")

    # sync chrome app policy resources
    chrome_gen_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome", "app", "policy", "android")
    sync(chrome_gen_res_dir, library_res_dir, "sync")

    # sync components generated string resources
    chrome_gen_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                          "gen", "components", "strings", "java", "res")
    sync(chrome_gen_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    chrome_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome", "android", "chrome_strings_grd_grit_output")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(chrome_grd_res_dir, library_res_dir, "sync", **args)

    # sync locale_paks
    zip_ref = zipfile.ZipFile(os.path.join(options.chromium_root, "out", options.buildtype,
                                           "gen", "chrome/android/chrome_locale_paks.resources.zip"), 'r')
    zip_ref.extractall(library_res_dir)
    zip_ref.close()

    # remove duplicate strings in android_chrome_strings.xml and generated_resources.xml
    #resource_util.remove_duplicated_strings(library_res_dir + '/values/android_chrome_strings.xml',
    #                                        library_res_dir + '/values/generated_resources.xml')

def sync_ui_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "ui_res", "src", "main", "res")
    ui_res_dir = os.path.join(options.chromium_root, "ui", "android", "java", "res")

    sync(ui_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    ui_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "ui", "android", "ui_strings_grd_grit_output")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(ui_grd_res_dir, library_res_dir, "sync", **args)

def sync_content_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "content_res", "src", "main", "res")
    content_res_dir = os.path.join(options.chromium_root, "content", "public", "android", "java", "res")
    sync(content_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    content_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                  "gen", "content", "public", "android", "content_strings_grd_grit_output")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(content_grd_res_dir, library_res_dir, "sync", **args)

def sync_datausagechart_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "datausagechart_res", "src", "main", "res")
    datausagechart_res_dir = os.path.join(options.chromium_root, "third_party", "android_data_chart", "java", "res")
    sync(datausagechart_res_dir, library_res_dir, "sync")

def sync_androidmedia_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "androidmedia_res", "src", "main", "res")
    media_res_dir = os.path.join(options.chromium_root, "third_party", "android_media", "java", "res")
    sync(media_res_dir, library_res_dir, "sync")

def sync_manifest_files(options):
    main_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main")
    public_apk_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome_public_apk_manifest")
    sync(public_apk_gen_dir, main_dir, "sync")

    # sync meta xml files
    xml_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "res", "xml")
    policy_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                  "gen", "policy")
    args = {'only': ['\S+\\.xml']}
    # TODO(alex)
    # sync(policy_gen_dir, xml_dir, "sync", **args)

def sync_data_files(options):
    # TODO(alex)
    locales_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "assets")
    pak_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype, "locales")
    args = {'only': ['en-US.pak', 'zh-CN.pak']}
    sync(pak_gen_dir, locales_dir, "sync", **args)

    assets_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "assets")
    chrome_public_assets_dir = os.path.join(options.chromium_root, "out", options.buildtype, "assets", "chrome_public_apk")
    #sync(chrome_public_assets_dir, assets_dir, "sync")

def main(argv):
    parser = optparse.OptionParser(usage='Usage: %prog [options]', description=__doc__)
    parser.add_option('--chromium_root',
                      default="/work/chromium/master/chromium-android/src",
                      help="The root of chromium sources")
    parser.add_option('--buildtype',
                      default="Default",
                      help="build type of chromium build")
    options, args = parser.parse_args(argv)

    sync_java_files(options)
    sync_jar_files(options)
    sync_chromium_res_files(options)
    sync_ui_res_files(options)
    sync_content_res_files(options)
    sync_datausagechart_res_files(options)
    sync_androidmedia_res_files(options)
    sync_manifest_files(options)
    sync_data_files(options)
    sync_so_files(options)

if __name__ == '__main__':
    main(sys.argv)