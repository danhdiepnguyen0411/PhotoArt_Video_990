---
name: android-nine-patch
description: Create, configure, and troubleshoot Android Nine-Patch (.9.png) drawables for scalable UI components with complex gradients, 3D glassmorphism, highlights, and glows. Use when implementing scalable buttons, cards, or speech bubbles where XML shapes are insufficient and bitmap scaling must preserve rounded corners or pill shapes.
---

# Android Nine-Patch (.9.png) Guide

## 1. Core Rules
- **Format**: Strictly `.9.png`. AAPT2 only parses nine-patch chunks on PNG. **WebP cannot be a Nine-Patch**.
- **Separation**: Background graphic only. Never bake text or icons into the asset (use dynamic `TextView` and Vector Drawables).
- **Density**: Place in `res/drawable-xxhdpi/` (or matching export density bucket).
- **1px Outer Border Integrity**:
  - Border must strictly contain **pure black** (`#000000`, 100% alpha) or **100% transparent** (`rgba(0,0,0,0)`).
  - Any semi-transparent, anti-aliased, or colored pixel in the 1px perimeter causes AAPT2 compilation errors.
- **Pill Button Geometry ($R = H/2$)**:
  - **Top Edge (Horizontal Stretch)**: 1–4 black pixels strictly in the flat center. Never place ticks over curved caps.
  - **Left Edge (Vertical Stretch)**: **Leave blank** for fixed-height buttons to prevent circular ends collapsing into ovals.
  - **Right Edge (Content Padding)**: Mark ticks **only** across the solid pill height so `TextView` centers inside the pill, not the glow.

## 2. Figma Box-Shadow & Glow Layout Formulas
In Figma, `boxShadow` renders outside bounds. In Android, `canvas.clipRect` confines the background to View bounds. A glow 9-patch includes glow margins within its intrinsic size.

| Dimension / Spacing | Formula | Example (56dp Solid + 12dp Glow) |
| :--- | :--- | :--- |
| **`layout_height`** | `Solid Height + 2 * Glow Radius` | `80dp` (never set `56dp`) |
| **`layout_marginHorizontal`** | `-Glow Radius` (inside padded container) | `-12dp` (restores 16dp solid edge alignment) |
| **Container Padding** | `Screen Margin - Glow Radius` (if dedicated bar) | `paddingHorizontal="4dp"` (16dp - 12dp) |
| **Vertical Gap to Next Button** | `Figma Gap - Glow Radius` | `layout_marginTop="4dp"` (16dp - 12dp) |
| **Parent ViewGroup** | Must disable clipping | `android:clipChildren="false"`<br>`android:clipToPadding="false"` |

## 3. Troubleshooting Quick Reference

| Issue / Symptom | Root Cause | Solution |
| :--- | :--- | :--- |
| **AAPT2 compile error** (`illegal character` / `chunk error`) | Dirty pixels (semi-transparent or colored) in outer 1px border. | Clean 1px border so every pixel is either pure black (`#000000`) or 100% transparent. |
| **Button width looks 24dp narrower** than cards/screen margins | 9-patch includes glow on left/right edges inside padded container. | Add `android:layout_marginHorizontal="-12dp"` to the button (or set container padding to `4dp`). |
| **Button height squashed (~39dp)** or ends distorted | `layout_height` set to `56dp` instead of intrinsic 9-patch height (`80dp`). | Set `android:layout_height="80dp"`; adjust vertical spacing with `layout_marginTop="4dp"`. |
| **Semicircular caps distorted into ovals** | Left edge has vertical stretch ticks on a fixed-height button. | Remove all Left-edge stretch ticks; match `layout_height` to intrinsic graphic height. |
| **Glow chopped into rectangular corners** | Missing glow margins on left/right caps, or parent ViewGroup is clipping children. | Ensure source asset has 360° glow margin (equal on all 4 sides); add `clipChildren="false"` to parent. |
| **Button text drifts / off-center** | Missing Right-edge content padding tick. | Add Right-edge ticks spanning strictly the solid pill height. |
