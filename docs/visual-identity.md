# Visual Identity & Color Palette — Coastal Midnight

This document serves as the future design reference for the visual identity and color palette used in the AlarMe application.

## Color Palette Summary

| Color Name | Hex Value | Android Resource Name | Primary Role & Intended Usage |
|---|---|---|---|
| **Coastal Blue** | `#447794` | `@color/coastal_blue` | Primary accent, Save button, FAB background, active/selected UI controls. |
| **Deep Coastal Blue** | `#2D5B75` | `@color/deep_coastal_blue` | Secondary emphasis, primary variant, pressed states, button borders/outlines. |
| **Ocean** | `#123249` | `@color/ocean` | Dark theme elevated surfaces, cards, and container backgrounds. |
| **Midnight** | `#061222` | `@color/midnight` | Dark theme main background and base surface color. |

---

## Theme Configuration

### Light Theme (`res/values/themes.xml`)
- **Base Surface & Window Background:** Light / `@color/white` (`#FFFFFF`)
- **Primary Text (`android:textColorPrimary`, `colorOnSurface`):** Midnight / `@color/midnight` (`#061222`)
- **Secondary Text (`android:textColorSecondary`):** Slate Blue / `@color/text_secondary` (`#5A6E7F`)
- **Card & Elevated Surfaces:** `@color/card_background` (`#FFFFFF`) with 1dp border (`#E2E8F0`)
- **Primary Accent (`colorPrimary`):** `@color/coastal_blue` (`#447794`)
- **Primary Variant (`colorPrimaryVariant`):** `@color/deep_coastal_blue` (`#2D5B75`)
- **Secondary Accent (`colorSecondary`):** `@color/deep_coastal_blue` (`#2D5B75`)
- **Foreground Text & Icons (`colorOnPrimary`, `colorOnSecondary`):** `@color/white` (`#FFFFFF`)
- **Theme Selector Container:** Rounded container (`#F0F4F8`) with 1dp border (`#D1DCE5`)
- **Theme Selector Option (Selected):** `@color/coastal_blue` fill with white text/icon
- **Theme Selector Option (Unselected):** Transparent fill with `@color/text_secondary` (`#5A6E7F`) text/icon

### Dark Theme (`res/values-night/themes.xml`)
- **Window Background & Base Surface (`colorSurface`):** `@color/midnight` (`#061222`)
- **Primary Text (`android:textColorPrimary`, `colorOnSurface`):** White / `@color/white` (`#FFFFFF`)
- **Secondary Text (`android:textColorSecondary`):** Muted Blue / `@color/text_secondary` (`#8FA3B5`)
- **Elevated Surfaces / Cards (`colorSurfaceVariant`, `cardBackgroundColor`):** `@color/ocean` (`#123249`) with 1dp border (`#1E3A52`)
- **Primary Accent (`colorPrimary`):** `@color/coastal_blue` (`#447794`)
- **Secondary Accent (`colorSecondary`):** `@color/coastal_blue` (`#447794`)
- **Foreground Text & Icons (`colorOnPrimary`, `colorOnSurface`):** `@color/white` (`#FFFFFF`)
- **Theme Selector Container:** Rounded container (`#061222`) with 1dp border (`#1E3A52`)
- **Theme Selector Option (Selected):** `@color/coastal_blue` fill with white text/icon
- **Theme Selector Option (Unselected):** Transparent fill with `@color/text_secondary` (`#8FA3B5`) text/icon
