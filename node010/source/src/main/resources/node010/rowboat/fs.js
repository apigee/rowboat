var fs = exports;

function Stats() {
}
fs.Stats = Stats;

fs.stat = function(path, cb) {
  // cb(err, stat), otherwise throw on err
};

fs.close = function(fd, cb) {
  // Throw if no cb
};

fs.open = function(path, flags, mode, cb) {
};

fs.read = function(fd, buffer, ofset, length, position, cb) {
  // cb is (err, bytesRead)
  // Otherwise return bytesRead
};

fs.write = function(fd, buffer, offset, length, position, cb) {
  // Same as read
};

fs.rename = function(oldPath, newPath, cb) {
  // Return new name?
};

fs.ftruncate = function(fd, len, cb) {
  // Return?
};

fs.rmdir = function(path, cb) {
  // Return?
};

fs.fdatasync = function(fd, cb) {
};

fs.fsync = function(fd, cb) {
};

fs.mkdir = function(path, mode, cb) {
  // Return?
};

fs.readdir = function(path, cb) {
  // Surely we return something
};

fs.fstat = function(fd, cb) {
};

fs.lstat = function(path, cb) {
};

fs.stat = function(path, cb) {
};

fs.readlink = function(path, cb) {
  // Return what we read in cb or return value
};

fs.symlink = function(dest, path, type, cb) {
};

fs.link = function(src, dest, cb) {
};

fs.unlink = function(path, cb) {
};

fs.fchmod = function(fd, mode, cb) {
};

fs.chmod = function(path, mode, cb) {
};

fs.fchown = function(fd, uid, gid, cb) {
};

fs.chown = function(path, uid, gid, cb) {
};

fs.utimes = function(path, atime, mtime, cb) {
};

fs.futimes = function(fd, atime, mtime, cb) {
};




