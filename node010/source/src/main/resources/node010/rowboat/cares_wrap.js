var IPAddressUtil = Java.type('sun.net.util.IPAddressUtil');

exports.isIP = function(addr) {
  if (!addr || (addr === '') || (addr === '0')) {
    return 0;
  }
  if (IPAddressUtil.isIPv4LiteralAddress(addr)) {
    return 4;
  }
  if (IPAddressUtil.isIPv6LiteralAddress(addr)) {
    return 6;
  }
};
