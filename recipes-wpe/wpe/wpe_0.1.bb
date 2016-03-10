SUMMARY = "WebKit for Wayland port pairs the WebKit engine with the Wayland display protocol, \
           allowing embedders to create simple and performant systems based on Web platform technologies. \
           It is designed with hardware acceleration in mind, relying on EGL, the Wayland EGL platform, and OpenGL ES."
HOMEPAGE = "http://www.webkitforwayland.org/"
LICENSE = "BSD & LGPLv2+"
LIC_FILES_CHKSUM = "file://Source/WebCore/LICENSE-LGPL-2.1;md5=a778a33ef338abbaf8b8a7c36b6eec80 "

DEPENDS += " \
    bison-native gperf-native harfbuzz-native ninja-native ruby-native \
    cairo fontconfig freetype glib-2.0 gnutls harfbuzz icu jpeg pcre sqlite3 udev zlib \
    libinput libpng libsoup-2.4 libwebp libxml2 libxslt \
    gstreamer1.0 \
    virtual/egl virtual/libgles2 \
"

PV = "0.1+git${SRCPV}"

SRCREV ?= "ec260b1ab6e603ae55e5ba6bfb289ba138fd95f9"
BASE_URI ?= "git://github.com/Metrological/WebKitForWayland.git;protocol=http;branch=master"
SRC_URI = "${BASE_URI}"
SRC_URI += "file://link-BCM-Nexus-backend-with-nxclient.patch"
SRC_URI += "file://0000-minimumAccelerated2dCanvasSize-to-275x256.patch"

# Workaround to allow musl toolchain libstdc++ to use libc ctype functions.
SRC_URI_append_libc-musl = " file://remove-disallow_ctypes_h-braindead.patch"

S = "${WORKDIR}/git"

inherit cmake pkgconfig perlnative pythonnative

WPE_BACKEND ?= "${@bb.utils.contains('DISTRO_FEATURES', 'wayland', 'wayland', 'rpi', d)}"

# The libprovision prebuilt libs currently support glibc ARM only.
PROVISIONING ?= "provisioning"
PROVISIONING_libc-musl = ""
PROVISIONING_mipsel = ""
PROVISIONING_x86 = ""

PACKAGECONFIG ?= "${WPE_BACKEND} ${PROVISIONING} logs video webaudio mediasource 2dcanvas fullscreenapi geolocation subtlecrypto shadowdom notifications gamepad deviceorientation"

# device specific configs
PACKAGECONFIG[intelce] = "-DUSE_WPE_BACKEND_INTEL_CE=ON -DUSE_HOLE_PUNCH_GSTREAMER=ON,,intelce-display,gstreamer1.0-fsmd"
PACKAGECONFIG[nexus] = "-DUSE_WPE_BACKEND_BCM_NEXUS=ON -DUSE_HOLE_PUNCH_GSTREAMER=ON,,broadcom-refsw"
PACKAGECONFIG[rpi] = "-DUSE_WPE_BACKEND_BCM_RPI=ON,,userland gstreamer1.0-omx"
PACKAGECONFIG[wayland] = "-DUSE_WPE_BACKEND_WAYLAND=ON -DUSE_WPE_BUFFER_MANAGEMENT_BCM_RPI=ON -DUSE_KEY_INPUT_HANDLING_LINUX_INPUT=OFF,-DUSE_KEY_INPUT_HANDLING_LINUX_INPUT=ON,wayland libxkbcommon"

# WPE features
PACKAGECONFIG[logs] = "-DLOG_DISABLED=OFF,-DLOG_DISABLED=ON,"
PACKAGECONFIG[video] = "-DENABLE_VIDEO=ON -DENABLE_VIDEO_TRACK=ON,-DENABLE_VIDEO=OFF -DENABLE_VIDEO_TRACK=OFF,gstreamer1.0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad,gstreamer1.0-plugins-base-app gstreamer1.0-plugins-base-playback gstreamer1.0-plugins-good-souphttpsrc"
PACKAGECONFIG[webaudio] = "-DENABLE_WEB_AUDIO=ON,-DENABLE_WEB_AUDIO=OFF,gstreamer1.0 gstreamer1.0-plugins-base gstreamer1.0-plugins-good,gstreamer1.0-plugins-good-wavparse"
PACKAGECONFIG[mediasource] = "-DENABLE_MEDIA_SOURCE=ON,-DENABLE_MEDIA_SOURCE=OFF,gstreamer1.0-plugins-good,gstreamer1.0-plugins-good-isomp4"
PACKAGECONFIG[encryptedmedia] = "-DENABLE_ENCRYPTED_MEDIA=ON,-DENABLE_ENCRYPTED_MEDIA=OFF,"
PACKAGECONFIG[2dcanvas] = "-DENABLE_ACCELERATED_2D_CANVAS=ON,-DENABLE_ACCELERATED_2D_CANVAS=OFF,"
PACKAGECONFIG[fullscreenapi] = "-DENABLE_FULLSCREEN_API=ON,-DENABLE_FULLSCREEN_API=OFF,"
PACKAGECONFIG[geolocation] = "-DENABLE_GEOLOCATION=ON,-DENABLE_GEOLOCATION=OFF,"
PACKAGECONFIG[subtlecrypto] = "-DENABLE_SUBTLE_CRYPTO=ON,-DENABLE_SUBTLE_CRYPTO=OFF,"
PACKAGECONFIG[shadowdom] = "-DENABLE_SHADOW_DOM=ON,-DENABLE_SHADOW_DOM=OFF,"
PACKAGECONFIG[notifications] = "-DENABLE_NOTIFICATIONS=ON,-DENABLE_NOTIFICATIONS=OFF,"
PACKAGECONFIG[gamepad] = "-DENABLE_GAMEPAD=ON,-DENABLE_GAMEPAD=OFF,"
PACKAGECONFIG[deviceorientation] = "-DENABLE_DEVICE_ORIENTATION=ON,-DENABLE_DEVICE_ORIENTATION=OFF,"
PACKAGECONFIG[mediastream] = "-DENABLE_MEDIA_STREAM=ON,-DENABLE_MEDIA_STREAM=OFF,openwebrtc"

# DRM
PACKAGECONFIG[provisioning] = "-DENABLE_PROVISIONING=ON,-DENABLE_PROVISIONING=OFF,libprovision,libprovision"
PACKAGECONFIG[playready] = "-DENABLE_PLAYREADY=ON,-DENABLE_PLAYREADY=OFF,playready"

EXTRA_OECMAKE += " \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_COLOR_MAKEFILE=OFF -DBUILD_SHARED_LIBS=ON -DPORT=WPE  \
  -G Ninja  \
"

# don't build debug
FULL_OPTIMIZATION_remove = "-g"

# WPEWebProcess crashes when built with ARM mode on RPi
ARM_INSTRUCTION_SET_armv7a = "thumb"
ARM_INSTRUCTION_SET_armv7ve = "thumb"

do_compile() {
   ${STAGING_BINDIR_NATIVE}/ninja ${PARALLEL_MAKE} libWPEWebKit.so libWPEWebInspectorResources.so WPEWebProcess WPENetworkProcess
}

do_install() {

    DESTDIR=${D} cmake -DCOMPONENT=Development -P ${B}/Source/WebKit2/cmake_install.cmake
    DESTDIR=${D} cmake -DCOMPONENT=Development -P ${B}/Source/JavaScriptCore/cmake_install.cmake

    install -d ${D}${libdir}
    cp -av ${B}/lib/libWPE.so* ${D}${libdir}/
    cp -av ${B}/lib/libWPEWebKit.so* ${D}${libdir}/
    install -m 0755 ${B}/lib/libWPEWebInspectorResources.so ${D}${libdir}/
    # Hack: Remove the RPATH embedded in libWPEWebKit.so
    chrpath --delete ${D}${libdir}/libWPEWebKit.so

    # Hack: Since libs were installed using 'cp', files will be owned by
    # the build user, which will trigger a QA warning. Fix them up manually.
    chown -R 0:0 ${D}${libdir}

    install -d ${D}${bindir}
    install -m755 ${B}/bin/WPENetworkProcess ${D}${bindir}/
    install -m755 ${B}/bin/WPEWebProcess ${D}${bindir}/

    # Hack: Remove RPATHs embedded in apps
    chrpath --delete ${D}${bindir}/WPENetworkProcess
    chrpath --delete ${D}${bindir}/WPEWebProcess
}

LEAD_SONAME = "libWPEWebKit.so"

PACKAGES =+ "${PN}-web-inspector-plugin"

FILES_${PN}-web-inspector-plugin += "${libdir}/libWPEWebInspectorResources.so"
INSANE_SKIP_${PN}-web-inspector-plugin = "dev-so"

# plugins-bad config option 'dash' -> gstreamer1.0-plugins-bad-dashdemux
# plugins-bad config option 'hls' -> gstreamer1.0-plugins-bad-fragmented
# plugins-bad config option 'videoparsers' -> gstreamer1.0-plugins-bad-videoparsersbad

RDEPENDS_${PN} += " \
    gstreamer1.0-plugins-base-audioconvert \
    gstreamer1.0-plugins-base-audioresample \
    gstreamer1.0-plugins-base-gio \
    gstreamer1.0-plugins-base-videoconvert \
    gstreamer1.0-plugins-base-videoscale \
    gstreamer1.0-plugins-base-volume \
    gstreamer1.0-plugins-base-typefindfunctions \
    gstreamer1.0-plugins-good-audiofx \
    gstreamer1.0-plugins-good-audioparsers \
    gstreamer1.0-plugins-good-autodetect \
    gstreamer1.0-plugins-good-avi \
    gstreamer1.0-plugins-good-deinterlace \
    gstreamer1.0-plugins-good-interleave \
    gstreamer1.0-plugins-bad-dashdemux \
    gstreamer1.0-plugins-bad-fragmented \
    gstreamer1.0-plugins-bad-mpegtsdemux \
    gstreamer1.0-plugins-bad-mpg123 \    
    gstreamer1.0-plugins-bad-smoothstreaming \
    gstreamer1.0-plugins-bad-videoparsersbad \
"

RDEPENDS_${PN}_append_rpi = "\
    gstreamer1.0-omx \
    gstreamer1.0-plugins-bad-faad \
    gstreamer1.0-plugins-bad-opengl \
"

RRECOMMENDS_${PN} += "ca-certificates"
