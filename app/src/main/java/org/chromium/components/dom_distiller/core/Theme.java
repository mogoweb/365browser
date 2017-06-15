package org.chromium.components.dom_distiller.core;
public enum Theme {
LIGHT(0),
DARK(1),
SEPIA(2),
THEME_COUNT(3),
;
private final int mValue;
private Theme(int value) {
  mValue = value;
}
int asNativeEnum() {
  return mValue;
}
static Theme getThemeForValue(int value) {
  for (Theme theme: Theme.values()) {
    if (theme.mValue == value) {
      return theme;
    }
  }
  return null;
}
}
