package org.chromium.components.dom_distiller.core;
public enum FontFamily {
SANS_SERIF(0),
SERIF(1),
MONOSPACE(2),
FONT_FAMILY_COUNT(3),
;
private final int mFontFamily;
private FontFamily(int value) {
  mFontFamily = value;
}
int asNativeEnum() {
  return mFontFamily;
}
public static FontFamily getFontFamilyForValue(int value) {
  for (FontFamily fontFamily: FontFamily.values()) {
    if (fontFamily.mFontFamily == value) {
      return fontFamily;
    }
  }
  return null;
}
}
