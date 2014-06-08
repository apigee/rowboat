var Constants = Java.type('io.apigee.rowboat.internal.Constants');
var Filesystem = Java.type('io.apigee.rowboat.internal.Filesystem');
var NodeOSException = Java.type('io.apigee.rowboat.internal.NodeOSException');

var fs = exports;
var binding = new Filesystem(process.getRuntime());

var debugEnabled;
var debug;
if (process.env.NODE_DEBUG && /fs/.test(process.env.NODE_DEBUG)) {
  debugEnabled = true;
  debug = function(x) { console.log(x); }
} else {
  debugEnabled = false;
  debug = function(x) {};
}

// Convert the statistics from a Map object returned by Java to a real object
function Stats(m) {
  this.size = m.size;
  this.dev = 0;
  var ino = m.fileKey;
  if (typeof ino === 'number') {
    this.ino = ino;
  } else {
    this.ino = ino.hashCode();
  }
  this.atime = new Date(m.lastAccessTime.toMillis());
  this.mtime = new Date(m.lastModifiedTime.toMillis());
  this.ctime = new Date(m.creationTime.toMillis());

  // This is a bit gross -- we can't actually get the real Unix UID of the user or group, but some
  // code -- notably NPM -- expects that this is returned as a number. So, returned the hashed
  // value, which is the best that we can do without native code.
  this.uid = (m.owner ? m.owner.hashCode() : 0);
  this.gid = (m.group ? m.group.hashCode() : 0);
  this.mode = binding.makeMode(m, p);
}
fs.Stats = Stats;

Stats.prototype.isFile = function() {
  return ((this.mode & Constants.S_IFREG) != 0);
};

Stats.prototype.isDirectory = function() {
  return ((this.mode & Constants.S_IFDIR) != 0);
};

Stats.prototype.isSymbolicLink = function() {
  return ((this.mode & Constants.S_IFLNK) != 0);
};

function no() {
  return false;
}

Stats.prototype.isBlockDevice = no;
Stats.prototype.isCharacterDevice = no;
Stats.prototype.isFIFO = no;
Stats.prototype.isSocket = no;

fs.stat = function(p, cb) {
  var path = String(p);
  if (cb) {
    // Basic pattern for async operations:
    // First function does operation and throws. Second one delivers the callback.
    binding.runAsync(function() {
      return new Stats(binding.stat(path, false));
    }, function(e, s) {
      cb(e, s);
    });
    return undefined;

  } else {
    // Basic pattern for sync operations: Do the operation, catch, and rethrow.
    try {
      return new Stats(binding.stat(path, false));
    } catch (e) {
      throw process.convertJavaException(e, path);
    }
  }
};

fs.fstat = function(fd, cb) {
  if (cb) {
    binding.runAsync(function() {
      return new Stats(binding.fstat(fd));
    }, function(e, s) {
      cb(e, s);
    });
    return undefined;

  } else {
    try {
      return new Stats(binding.fstat(fd));
    } catch (e) {
      throw process.convertJavaException(e, path);
    }
  }
};

fs.lstat = function(p, cb) {
  var path = String(p);
  if (cb) {
    binding.runAsync(function() {
      return new Stats(binding.stat(path, true));
    }, function(e, s) {
      cb(e, s);
    });
    return undefined;

  } else {
    try {
      return new Stats(binding.stat(path, true));
    } catch (e) {
      throw process.convertJavaException(e, path);
    }
  }
};

fs.open = function(p, flags, mode, cb) {
  var path = String(p);
  if (cb) {
    binding.runAsync(function() {
      return binding.open(path, flags, mode);
    }, function(e, fd) {
      cb(e, fd);
    });
    return undefined;

  } else {
    try {
      return binding.open(path, flags, mode);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.close = function(fd, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.close(fd);
    }, cb);

  } else {
    try {
      binding.close(fd);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.read = function(fd, buffer, offset, length, position, cb) {
  if (offset >= buffer.length) {
    throw new Error(Constants.EINVAL);
  }
  if ((offset + length) > buffer.length) {
    throw new Error(Constants.EINVAL);
  }

  if (cb) {
    binding.readAsync(fd, buffer.toJava(), offset, length, position, function(err, count) {
      if (err) {
        cb(err, 0, buffer);
      } else {
        cb(err, count, buffer);
      }
    });
    return undefined;
  }

  try {
    return binding.readSync(fd, buffer.toJava(), offset, length, position);
  } catch (e) {
    throw process.convertJavaException(e);
  }
};

fs.write = function(fd, buffer, offset, length, position, cb) {
  if (offset >= buffer.length) {
    throw new Error(Constants.EINVAL);
  }
  if ((offset + length) > buffer.length) {
    throw new Error(Constants.EINVAL);
  }

  if (cb) {
    binding.writeAsync(fd, buffer.toJava(), offset, length, position, function(err, count) {
      if (err) {
        cb(err, 0, buffer);
      } else {
        cb(err, count, buffer.toJava());
      }
    });
    return undefined;
  }

  try {
    return binding.writeSync(fd, buffer.toJava(), offset, length, position);
  } catch (e) {
    throw process.convertJavaException(e);
  }
};

fs.rename = function(oldPath, newPath, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.rename(String(oldPath), String(newPath));
    }, cb);

  } else {
    try {
      binding.rename(String(oldPath), String(newPath));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.ftruncate = function(fd, len, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.ftruncate(fd, len);
    }, cb);

  } else {
    try {
      binding.ftruncate(fd, len);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.rmdir = function(path, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.rmdir(String(path));
    }, cb);

  } else {
    try {
      binding.rmdir(String(path));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.fdatasync = function(fd, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.doSync(fd, false);
    }, cb);

  } else {
    try {
      binding.doSync(fd, false);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.fsync = function(fd, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.doSync(fd, true);
    }, cb);

  } else {
    try {
      binding.doSync(fd, true);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.mkdir = function(path, mode, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.mkdir(String(path), mode);
    }, cb);

  } else {
    try {
      binding.mkdir(String(path), mode);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.readdir = function(path, cb) {
  if (cb) {
    binding.runAsync(function() {
      return Java.from(binding.readdir(String(path)));
    }, cb);
    return undefined;
  }

  try {
    return Java.from(binding.readdir(String(path)));
  } catch (e) {
    throw process.convertJavaException(e);
  }
};

fs.readlink = function(path, cb) {
  if (cb) {
    binding.runAsync(function() {
      return binding.readlink(String(path));
    }, cb);
    return undefined;
  }

  try {
    return binding.readlink(String(path));
  } catch (e) {
    throw process.convertJavaException(e);
  }
};

fs.symlink = function(dest, path, type, cb) {
  // Trireme and Java don't do anything with the "type" parameter
  if (cb) {
    binding.runAsync(function() {
      binding.symlink(String(dest), String(path));
    }, cb);
  } else {
    try {
      binding.symlink(String(dest), String(path));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.link = function(src, dest, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.link(String(dest), String(path));
    }, cb);
  } else {
    try {
      binding.link(String(dest), String(path));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.unlink = function(path, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.unlink(String(path));
    }, cb);
  } else {
    try {
      binding.unlink(String(path));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.fchmod = function(fd, mode, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.fchmod(fd, mode);
    }, cb);
  } else {
    try {
      binding.fchmod(fd, mode);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.chmod = function(path, mode, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.chmod(String(path), mode);
    }, cb);
  } else {
    try {
      binding.chmod(String(path), mode);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.fchown = function(fd, uid, gid, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.fchown(fd, String(uid), String(gid));
    }, cb);
  } else {
    try {
      binding.fchown(fd, String(uid), String(gid));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.chown = function(path, uid, gid, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.chown(String(path), String(uid), String(gid));
    }, cb);
  } else {
    try {
      binding.chown(String(path), String(uid), String(gid));
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.utimes = function(path, atime, mtime, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.utimes(String(path), atime, mtime);
    }, cb);
  } else {
    try {
      binding.utimes(String(path), atime, mtime);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};

fs.futimes = function(fd, atime, mtime, cb) {
  if (cb) {
    binding.runAsync(function() {
      binding.futimes(fd, atime, mtime);
    }, cb);
  } else {
    try {
      binding.futimes(fd, atime, mtime);
    } catch (e) {
      throw process.convertJavaException(e);
    }
  }
};
