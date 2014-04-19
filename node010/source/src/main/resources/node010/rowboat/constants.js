var Constants = Java.type('io.apigee.rowboat.internal.Constants');
var c = exports;

c.O_APPEND = Constants.O_APPEND;
c.O_CREAT = Constants.O_CREAT;
c.O_DIRECTORY = Constants.O_DIRECTORY;
c.O_EXCL = Constants.O_EXCL;
c.O_NOCTTY = Constants.O_NOCTTY;
c.O_NOFOLLOW = Constants.O_NOFOLLOW;
c.O_RDONLY = Constants.O_RDONLY;
c.O_RDWR = Constants.O_RDWR;
// See above regarding "lchmod"
// c.O_SYMLINK
c.O_SYNC = Constants.O_SYNC;
c.O_TRUNC = Constants.O_TRUNC;
c.O_WRONLY = Constants.O_WRONLY;

c.S_IFDIR = Constants.S_IFDIR;
c.S_IFREG = Constants.S_IFREG;
c.S_IFBLK = Constants.S_IFBLK;
c.S_IFCHR = Constants.S_IFCHR;
c.S_IFLNK = Constants.S_IFLNK;
c.S_IFIFO = Constants.S_IFIFO;
c.S_IFSOCK = Constants.S_IFSOCK;
c.S_IFMT = Constants.S_IFMT;

c.SIGHUP = Constants.SIGHUP;
c.SIGINT = Constants.SIGINT;
c.SIGKILL = Constants.SIGKILL;
c.SIGTERM = Constants.SIGTERM;
c.SIGQUIT = Constants.SIGQUIT;
