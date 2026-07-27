/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.harassmentblock;

/**
 * The two supported blocking strategies for the harassment-blocking (骚扰拦截) feature, mirroring
 * the "黑名单/白名单" toggle found in most domestic Android skins (MIUI/EMUI/ColorOS-style dialers).
 */
public enum HarassmentBlockMode {
  /** Only calls matching a blacklist rule are blocked; everything else rings normally. */
  BLACKLIST,

  /**
   * Only calls matching a whitelist rule (or from a saved contact) are allowed to ring; everything
   * else is blocked.
   */
  WHITELIST
}
