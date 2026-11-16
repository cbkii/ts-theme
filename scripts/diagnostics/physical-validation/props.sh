#!/system/bin/sh
# Exact, limited property allowlist. Avoid dumping all private system properties.
PATH=/system/bin:/vendor/bin:/product/bin; export PATH
for key in \
  ro.product.model ro.product.device ro.product.board ro.board.platform ro.hardware \
  ro.build.version.release ro.build.version.sdk ro.build.fingerprint \
  ro.build.version.incremental ro.build.version.security_patch \
  ro.tw.version ro.fota.oem ro.fota.platform ro.fota.device ro.fota.version \
  ro.product.cpu.abi ro.product.cpu.abilist ro.boot.verifiedbootstate \
  ro.boot.flash.locked ro.boot.vbmeta.device_state ro.boot.veritymode \
  ro.zygote ro.config.low_ram ro.sf.lcd_density \
  sys.boot_completed dev.bootcomplete init.svc.surfaceflinger \
  init.svc.zygote init.svc.zygote_secondary sys.user.0.ce_available \
  persist.tw.theme.mode persist.tw.bt.module persist.sys.tw.link
 do
   printf '%s=' "$key"
   /system/bin/getprop "$key" 2>/dev/null
 done
