/*
 * Copyright 2014 Apigee Corporation.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.apigee.rowboat.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class presents a generic filesystem implementation that we can easily call directly
 * from JavaScript.
 */
public class Filesystem
{
    private static final Logger log = LoggerFactory.getLogger(Filesystem.class.getName());

    private static final int FIRST_FD = 4;

    private final ScriptRunner runtime;

    private final AtomicInteger nextFd = new AtomicInteger(FIRST_FD);
    private final ConcurrentHashMap<Integer, FileHandle> descriptors = new ConcurrentHashMap<>();

    public Filesystem(ScriptRunner runtime)
    {
        this.runtime = runtime;
    }

    private Path translatePath(String path)
        throws NodeOSException
    {
        File trans = runtime.translatePath(path);
        if (trans == null) {
            throw new NodeOSException(Constants.ENOENT, path);
        }
        return Paths.get(trans.getPath());
    }

    private String getErrorCode(Throwable ioe)
    {
        String code = Constants.EIO;
        if (ioe instanceof FileNotFoundException) {
            code = Constants.ENOENT;
        } else if (ioe instanceof AccessDeniedException) {
            code = Constants.EPERM;
        } else if (ioe instanceof DirectoryNotEmptyException) {
            code = Constants.ENOTEMPTY;
        } else if (ioe instanceof FileAlreadyExistsException) {
            code = Constants.EEXIST;
        } else if (ioe instanceof NoSuchFileException) {
            code = Constants.ENOENT;
        } else if (ioe instanceof NotDirectoryException) {
            code = Constants.ENOTDIR;
        } else if (ioe instanceof NotLinkException) {
            code = Constants.EINVAL;
        }
        if (log.isDebugEnabled()) {
            log.debug("File system error {} = code {}", ioe, code);
        }
        return code;
    }

    /**
     * This is the basis of our async filesystem support. It takes two functions. The first takes no
     * arguments and returns a result. This first function is run in a separate thread pool because
     * we are expecting it to block. The second function takes two arguments, the first of which is
     * an error and the second is the actual result. It is run in the main script thread pool.
     * The result of the first function is passed as the second argument to the second one.
     */
    @SuppressWarnings("unused")
    public void runAsync(Supplier<Object> operation,
                         BiFunction<Object, Object, Object> resultHandler)
    {
        runtime.pin();
        runtime.getAsyncPool().submit(() -> {
            try {
                Object result = operation.get();
                runtime.enqueueTask(() -> {
                    resultHandler.apply(null, result);
                });
            } catch (Throwable t) {
                runtime.enqueueTask(() -> {
                    resultHandler.apply(runtime.convertError(t), null);
                });
            } finally {
                runtime.unPin();
            }
        });
    }

    private Map<String, Object> readAttrs(String attrNames, Path p,
                                          boolean noFollow)
        throws IOException
    {
        if (noFollow) {
            return Files.readAttributes(p, attrNames,
                                        LinkOption.NOFOLLOW_LINKS);
        }
        return Files.readAttributes(p, attrNames);
    }

    private Map<String, Object> doStat(Path p, boolean noFollow)
    {
        try {
            Map<String, Object> attrs;

            if (Platform.get().isPosixFilesystem()) {
                attrs = readAttrs("posix:*", p, noFollow);
            } else {
                // The Map returned by "readAttributes" can't be modified
                attrs = new HashMap<>();
                attrs.putAll(readAttrs("*", p, noFollow));
                attrs.putAll(readAttrs("owner:*", p, noFollow));
            }

            if (log.isTraceEnabled()) {
                log.trace("stat {} = {}", p, attrs);
            }

            return attrs;

        } catch (IOException ioe) {
            if (log.isTraceEnabled()) {
                log.trace("stat {} (nofollow = {}) = {}", p, noFollow, ioe);
            }
            throw new NodeOSException(getErrorCode(ioe), ioe, p.toString());
        }
    }

    @SuppressWarnings("unused")
    public Map<String, Object> stat(String path, boolean noFollow)
        throws NodeOSException
    {
        Path p = translatePath(path);
        return doStat(p, noFollow);
    }

    @SuppressWarnings("unused")
    public Map<String, Object> fstat(int fd)
        throws NodeOSException
    {
        FileHandle fh = ensureHandle(fd);
        return doStat(fh.path, fh.noFollow);
    }

    @SuppressWarnings("unused")
    public int makeMode(Map<String, Object> attrs, String path)
    {
        int mode = 0;

        if ((Boolean)attrs.get("isRegularFile")) {
            mode |= Constants.S_IFREG;
        }
        if ((Boolean)attrs.get("isDirectory")) {
            mode |= Constants.S_IFDIR;
        }
        if ((Boolean)attrs.get("isSymbolicLink")) {
            mode |= Constants.S_IFLNK;
        }

        if (attrs.containsKey("permissions")) {
            Set<PosixFilePermission> perms =
                (Set<PosixFilePermission>)attrs.get("permissions");
            mode |= setPosixPerms(perms);
        } else {
            mode |= setNonPosixPerms(translatePath(path));
        }
        return mode;
    }

    public int setPosixPerms(Set<PosixFilePermission> perms)
    {
        int mode = 0;
        // Posix file perms
        if (perms.contains(PosixFilePermission.GROUP_EXECUTE)) {
            mode |= Constants.S_IXGRP;
        }
        if (perms.contains(PosixFilePermission.GROUP_READ)) {
            mode |= Constants.S_IRGRP;
        }
        if (perms.contains(PosixFilePermission.GROUP_WRITE)) {
            mode |= Constants.S_IWGRP;
        }
        if (perms.contains(PosixFilePermission.OTHERS_EXECUTE)) {
            mode |= Constants.S_IXOTH;
        }
        if (perms.contains(PosixFilePermission.OTHERS_READ)) {
            mode |= Constants.S_IROTH;
        }
        if (perms.contains(PosixFilePermission.OTHERS_WRITE)) {
            mode |= Constants.S_IWOTH;
        }
        if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
            mode |= Constants.S_IXUSR;
        }
        if (perms.contains(PosixFilePermission.OWNER_READ)) {
            mode |= Constants.S_IRUSR;
        }
        if (perms.contains(PosixFilePermission.OWNER_WRITE)) {
            mode |= Constants.S_IWUSR;
        }
        return mode;
    }

    public int setNonPosixPerms(Path p)
    {
        File file = p.toFile();
        int mode = 0;

        if (file.canRead()) {
            mode |= Constants.S_IRUSR;
        }
        if (file.canWrite()) {
            mode |= Constants.S_IWUSR;
        }
        if (file.canExecute()) {
            mode |= Constants.S_IXUSR;
        }
        return mode;
    }

    @SuppressWarnings("unused")
    public int open(String pathStr, int flags, int mode)
        throws NodeOSException
    {
        if (log.isDebugEnabled()) {
            log.debug("open({}, {}, {})", pathStr, flags, mode);
        }

        Path path = translatePath(pathStr);
        AsynchronousFileChannel file = null;

        // To support "lchmod", we need to check "O_SYMLINK" here too
        if (!Files.isDirectory(path)) {
            // Open an AsynchronousFileChannel using all the relevant open options.
            // But if we are opening a symbolic link or directory, just record the path and go on
            HashSet<OpenOption> options = new HashSet<OpenOption>();
            if ((flags & Constants.O_CREAT) != 0) {
                if ((flags & Constants.O_EXCL) != 0) {
                    options.add(StandardOpenOption.CREATE_NEW);
                } else {
                    options.add(StandardOpenOption.CREATE);
                }
            }
            if ((flags & Constants.O_RDWR) != 0) {
                options.add(StandardOpenOption.READ);
                options.add(StandardOpenOption.WRITE);
            } else if ((flags & Constants.O_WRONLY) != 0) {
                options.add(StandardOpenOption.WRITE);
            } else {
                options.add(StandardOpenOption.READ);
            }

            if ((flags & Constants.O_TRUNC) != 0) {
                options.add(StandardOpenOption.TRUNCATE_EXISTING);
            }
            if ((flags & Constants.O_SYNC) != 0) {
                options.add(StandardOpenOption.SYNC);
            }

            try {
                if (log.isDebugEnabled()) {
                    log.debug("Opening {} with {}", path, options);
                }
                if (Platform.get().isPosixFilesystem()) {
                    file = AsynchronousFileChannel.open(path, options, runtime.getAsyncPool(),
                                                        PosixFilePermissions.asFileAttribute(modeToPerms(mode, true)));
                } else {
                    file = AsynchronousFileChannel.open(path, options, runtime.getAsyncPool(),
                                                        new FileAttribute<?>[0]);
                    setModeNoPosix(path, mode);
                }

            } catch (IOException ioe) {
                throw new NodeOSException(getErrorCode(ioe), ioe, pathStr);
            }
        }

        try {
            FileHandle fileHandle = new FileHandle(path, file);
            // Replace this if we choose to support "lchmod"
                /*
                if ((flags & Constants.O_SYMLINK) != 0) {
                    fileHandle.noFollow = true;
                }
                */
            if (((flags & Constants.O_APPEND) != 0) && (file != null) && (file.size() > 0)) {
                if (log.isDebugEnabled()) {
                    log.debug("  setting file position to {}", file.size());
                }
                fileHandle.position = file.size();
            }

            int fd = nextFd.getAndIncrement();
            descriptors.put(fd, fileHandle);
            return fd;

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, pathStr);
        }
    }

    @SuppressWarnings("unused")
    public void close(int fd)
        throws NodeOSException
    {
        FileHandle handle = ensureHandle(fd);
        try {
            if (log.isDebugEnabled()) {
                log.debug("close({})", fd);
            }
            if (handle.file != null) {
                handle.file.close();
            }
            descriptors.remove(fd);
        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe);
        }
    }

    @SuppressWarnings("unused")
    public int readSync(int fd, ByteBuffer readBuf, int offset, int length, long pos)
        throws NodeOSException
    {
        FileHandle handle = ensureRegularFileHandle(fd);
        long position = (pos < 0L ? handle.position : pos);
        readBuf.position(readBuf.position() + offset);
        readBuf.limit(readBuf.position() + length);

        try {
            Future<Integer> result = handle.file.read(readBuf, position);
            int count = result.get();

            // Node (like C) expects 0 on EOF, not -1
            if (count < 0) {
                count = 0;
            }
            handle.position += count;

            if (log.isDebugEnabled()) {
                log.debug("read({}, {}, {}) = {}",
                          offset, length, pos, count);
            }
            return count;

        } catch (InterruptedException ie) {
            throw new NodeOSException(Constants.EINTR);
        } catch (ExecutionException ee) {
            throw new NodeOSException(getErrorCode(ee.getCause()), ee.getCause());
        }
    }

    @SuppressWarnings("unused")
    public void readAsync(int fd, ByteBuffer readBuf, int offset, int length, long pos,
                          BiFunction<Object, Integer, Object> cb)
    {
        FileHandle handle = ensureRegularFileHandle(fd);
        long position = (pos < 0L ? handle.position : pos);
        readBuf.position(readBuf.position() + offset);
        readBuf.limit(readBuf.position() + length);

        runtime.pin();
        handle.file.read(readBuf, position, null,
                         new CompletionHandler<Integer, Object>() {
                             @Override
                             public void completed(Integer result, Object attachment)
                             {
                                 int count = (result < 0 ? 0 : result);
                                 if (log.isDebugEnabled()) {
                                     log.debug("async read({}, {}, {}) = {}",
                                               offset, length, position, count);
                                 }
                                 handle.position += count;

                                 runtime.enqueueTask(() -> {
                                     cb.apply(null, count);
                                 });
                                 runtime.unPin();
                             }

                             @Override
                             public void failed(Throwable t, Object attachment)
                             {
                                 NodeOSException ne = new NodeOSException(getErrorCode(t));
                                 runtime.enqueueTask(() -> {
                                     cb.apply(runtime.convertError(ne), null);
                                 });
                                 runtime.unPin();
                             }
                         });
    }

    @SuppressWarnings("unused")
    public int writeSync(int fd, ByteBuffer writeBuf, int offset, int length, long pos)
        throws NodeOSException
    {
        FileHandle handle = ensureRegularFileHandle(fd);
        long position = (pos < 0L ? handle.position : pos);
        writeBuf.position(writeBuf.position() + offset);
        writeBuf.limit(writeBuf.position() + length);

        try {
            Future<Integer> result = handle.file.write(writeBuf, position);
            int count = result.get();
            handle.position += count;

            if (log.isDebugEnabled()) {
                log.debug("write({}, {}, {}) = {}",
                          offset, length, pos, count);
            }
            return count;

        } catch (InterruptedException ie) {
            throw new NodeOSException(Constants.EINTR);
        } catch (ExecutionException ee) {
            throw new NodeOSException(getErrorCode(ee.getCause()), ee.getCause());
        }
    }

    @SuppressWarnings("unused")
    public void writeAsync(int fd, ByteBuffer writeBuf, int offset, int length, long pos,
                          BiFunction<Object, Integer, Object> cb)
    {
        FileHandle handle = ensureRegularFileHandle(fd);
        long position = (pos < 0L ? handle.position : pos);
        writeBuf.position(writeBuf.position() + offset);
        writeBuf.limit(writeBuf.position() + length);

        // To make certain tests pass, we'll pre-increment the file position before writing
        // This doesn't make it a whole lot safer to issue a lot of async writes though
        handle.position += writeBuf.remaining();

        runtime.pin();
        handle.file.write(writeBuf, position, null,
                          new CompletionHandler<Integer, Object>() {
                             @Override
                             public void completed(Integer result, Object attachment)
                             {
                                 int count = (result < 0 ? 0 : result);
                                 if (log.isDebugEnabled()) {
                                     log.debug("async write({}, {}, {}) = {}",
                                               offset, length, position, count);
                                 }

                                 runtime.enqueueTask(() -> {
                                     cb.apply(null, count);
                                 });
                                 runtime.unPin();
                             }

                             @Override
                             public void failed(Throwable t, Object attachment)
                             {
                                 NodeOSException ne = new NodeOSException(getErrorCode(t));
                                 runtime.enqueueTask(() -> {
                                     cb.apply(runtime.convertError(ne), null);
                                 });
                                 runtime.unPin();
                             }
                         });
    }

    @SuppressWarnings("unused")
    public void rename(String oldPath, String newPath)
        throws NodeOSException
    {
        Path oldFile = translatePath(oldPath);
        Path newFile = translatePath(newPath);

        try {
            Files.copy(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, oldPath);
        }
    }

    @SuppressWarnings("unused")
    public void ftruncate(int fd, long len)
        throws NodeOSException
    {
        if (log.isDebugEnabled()) {
            log.debug("ftruncate({}, {})", fd, len);
        }
        try {
            FileHandle handle = ensureRegularFileHandle(fd);
            if (len > handle.file.size()) {
                // AsynchronousFileChannel doesn't actually extend the file size, so do it a different way
                RandomAccessFile tmp = new RandomAccessFile(handle.path.toFile(), "rw");
                try {
                    tmp.setLength(len);
                } finally {
                    tmp.close();
                }
            } else {
                handle.file.truncate(len);
            }
        } catch (IOException e) {
            throw new NodeOSException(getErrorCode(e), e);
        }
    }

    @SuppressWarnings("unused")
    public void rmdir(String path)
        throws NodeOSException
    {
        if (log.isDebugEnabled()) {
            log.debug("rmdir({})", path);
        }
        Path p = translatePath(path);
        if (!Files.isDirectory(p)) {
            throw new NodeOSException(Constants.ENOTDIR, path);
        }

        try {
            Files.delete(p);
        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, path);
        }
    }

    @SuppressWarnings("unused")
    public void doSync(int fd, boolean metaData)
        throws NodeOSException
    {
        FileHandle handle = ensureRegularFileHandle(fd);
        if (log.isDebugEnabled()) {
            log.debug("fsync({})", fd);
        }
        try {
            handle.file.force(metaData);
        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe);
        }
    }

    @SuppressWarnings("unused")
    public void mkdir(String path, int mode)
        throws NodeOSException
    {
        if (log.isDebugEnabled()) {
            log.debug("mkdir({})", path);
        }
        Path p  = translatePath(path);

        try {
            if (Platform.get().isPosixFilesystem()) {
                Set<PosixFilePermission> perms = modeToPerms(mode, true);
                Files.createDirectory(p,
                                      PosixFilePermissions.asFileAttribute(perms));
            } else {
                Files.createDirectory(p);
                setModeNoPosix(p, mode);
            }

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, path);
        }
    }

    @SuppressWarnings("unused")
    public List<String> readdir(String dn)
        throws NodeOSException
    {
        Path sp = translatePath(dn);
        if (!Files.isDirectory(sp)) {
            throw new NodeOSException(Constants.ENOTDIR, sp.toString());
        }
        try {
            final ArrayList<String> paths = new ArrayList<String>();
            Set<FileVisitOption> options = Collections.emptySet();
            Files.walkFileTree(sp, options, 1,
                               new SimpleFileVisitor<Path>() {
                                   @Override
                                   public FileVisitResult visitFile(Path child, BasicFileAttributes attrs)
                                   {
                                       if (log.isTraceEnabled()) {
                                           log.trace("  " + child.getFileName());
                                       }
                                       paths.add(child.getFileName().toString());
                                       return FileVisitResult.CONTINUE;
                                   }
                               });
            return paths;

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, dn);
        }
    }

    @SuppressWarnings("unused")
    public String readlink(String pathStr)
        throws NodeOSException
    {
        Path path = translatePath(pathStr);

        try {
            Path target = Files.readSymbolicLink(path);
            if (log.isDebugEnabled()) {
                log.debug("readLink({}) = {}", path, target);
            }

            String result;
            if (Files.isDirectory(target)) {
                // There is a test that expects this.
                return target.toString() + '/';
            }
            return target.toString();

        } catch (IOException ioe) {
            log.debug("IOException: {}", ioe);
            throw new NodeOSException(getErrorCode(ioe), ioe, pathStr);
        }
    }

    @SuppressWarnings("unused")
    public void symlink(String destPath, String srcPath)
        throws NodeOSException
    {
        Path dest = translatePath(destPath);
        Path src = translatePath(srcPath);

        if (dest == null) {
            throw new NodeOSException(Constants.EPERM, "Attempt to link file above filesystem root");
        }

        // "symlink" supports relative paths. But now that we have checked to make sure that we're
        // not trying to link an "illegal" path, we can just use the original path if it is relative.
        Path origSrc = Paths.get(srcPath);
        if (!origSrc.isAbsolute()) {
            src = origSrc;
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("symlink from {} to {}",
                          src, dest);
            }

            Files.createSymbolicLink(dest, src);

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, destPath);
        }
    }

    @SuppressWarnings("unused")
    public void link(String destPath, String srcPath)
        throws NodeOSException
    {
        Path dest = translatePath(destPath);
        Path src = translatePath(srcPath);

        try {
            if (log.isDebugEnabled()) {
                log.debug("link from {} to {}",
                          src, dest);
            }
            Files.createLink(dest, src);

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, destPath);
        }
    }

    @SuppressWarnings("unused")
    public void unlink(String path)
        throws NodeOSException
    {
        if (log.isDebugEnabled()) {
            log.debug("unlink({})", path);
        }
        Path p = translatePath(path);

        try {
            Files.delete(p);

        } catch (DirectoryNotEmptyException dne) {
            // Special case because unlinking a directory should be a different error.
            throw new NodeOSException(Constants.EPERM, dne, path);
        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, path);
        }
    }

    private void doChmod(Path path, int mode, boolean noFollow)
        throws NodeOSException
    {
        Set<PosixFilePermission> perms = modeToPerms(mode, false);

        if (log.isDebugEnabled()) {
            log.debug("chmod({}, {}) to {}", path, mode, perms);
        }

        try {
            if (noFollow) {
                Files.setAttribute(path, "posix:permissions", perms, LinkOption.NOFOLLOW_LINKS);
            } else {
                Files.setAttribute(path, "posix:permissions", perms);
            }

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, path.toString());
        }
    }

    @SuppressWarnings("unused")
    public void chmod(String path, int mode)
        throws NodeOSException
    {
        Path p = translatePath(path);
        if (Platform.get().isPosixFilesystem()) {
            doChmod(p, mode, false);
        } else {
            setModeNoPosix(p, mode);
        }
    }

    @SuppressWarnings("unused")
    public void fchmod(int fd, int mode)
        throws NodeOSException
    {
        FileHandle fh = ensureHandle(fd);
        if (Platform.get().isPosixFilesystem()) {
            doChmod(fh.path, mode, fh.noFollow);
        } else {
            setModeNoPosix(fh.path, mode);
        }
    }

    private void doChown(Path path, String uid, String gid, boolean noFollow)
        throws NodeOSException
    {
        if (log.isDebugEnabled()) {
            log.debug("chown({}) to {}:{}", path, uid, gid);
        }

        UserPrincipalLookupService lookupService =
            FileSystems.getDefault().getUserPrincipalLookupService();

        // In Java, we can't actually get the unix UID, so we take a username here, rather
        // than a UID. That may cause problems for NPM, which may try to use a UID.
        try {
            UserPrincipal user = lookupService.lookupPrincipalByName(uid);

            if (Platform.get().isPosixFilesystem()) {
                GroupPrincipal group = lookupService.lookupPrincipalByGroupName(gid);

                if (noFollow) {
                    Files.setAttribute(path, "posix:owner", user, LinkOption.NOFOLLOW_LINKS);
                    Files.setAttribute(path, "posix:group", group, LinkOption.NOFOLLOW_LINKS);
                } else {
                    Files.setAttribute(path, "posix:owner", user);
                    Files.setAttribute(path, "posix:group", group);
                }

            } else {
                Files.setOwner(path, user);
            }

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, path.toString());
        }
    }

    @SuppressWarnings("unused")
    public void chown(String path, String uid, String gid)
        throws NodeOSException
    {
        Path p = translatePath(path);
        doChown(p, uid, gid, false);
    }

    @SuppressWarnings("unused")
    public void fchown(int fd, String uid, String gid)
        throws NodeOSException
    {
        FileHandle fh = ensureHandle(fd);
        doChown(fh.path, uid, gid, fh.noFollow);
    }

    private void doUTimes(Path path, double atime, double mtime, boolean nofollow)
        throws NodeOSException
    {
        try {
            BasicFileAttributeView attrView;
            if (nofollow) {
                attrView = Files.getFileAttributeView(path, BasicFileAttributeView.class,
                                                      LinkOption.NOFOLLOW_LINKS);
            } else {
                attrView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
            }

            BasicFileAttributes attrs = attrView.readAttributes();
            // The timestamp seems to come from JavaScript as a decimal value of seconds
            FileTime newATime = FileTime.fromMillis((long)(atime * 1000.0));
            FileTime newMTime = FileTime.fromMillis((long)(mtime * 1000.0));
            attrView.setTimes(newMTime, newATime, attrs.creationTime());

        } catch (IOException ioe) {
            throw new NodeOSException(getErrorCode(ioe), ioe, path.toString());
        }
    }

    @SuppressWarnings("unused")
    public void utimes(String path, double atime, double mtime)
        throws NodeOSException
    {
        Path p = translatePath(path);
        doUTimes(p, atime, mtime, false);
    }

    @SuppressWarnings("unused")
    public void futimes(int fd, double atime, double mtime)
        throws NodeOSException
    {
        FileHandle fh = ensureHandle(fd);
        doUTimes(fh.path, atime, mtime, fh.noFollow);
    }

    private Set<PosixFilePermission> modeToPerms(int origMode, boolean onCreate)
    {
        int mode;
        if (onCreate) {
            // Umask only applies when creating a file, not when changing mode
            mode = origMode & (~(runtime.getUmask()));
        } else {
            mode = origMode;
        }
        Set<PosixFilePermission> perms =
            EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & Constants.S_IXUSR) != 0) {
            perms.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & Constants.S_IRUSR) != 0) {
            perms.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & Constants.S_IWUSR) != 0) {
            perms.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & Constants.S_IXGRP) != 0) {
            perms.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & Constants.S_IRGRP) != 0) {
            perms.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & Constants.S_IWGRP) != 0) {
            perms.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & Constants.S_IXOTH) != 0) {
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        if ((mode & Constants.S_IROTH) != 0) {
            perms.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & Constants.S_IWOTH) != 0) {
            perms.add(PosixFilePermission.OTHERS_WRITE);
        }
        if (log.isDebugEnabled()) {
            log.debug("Mode {} and {} becomes {} then {}",
                      Integer.toOctalString(origMode), Integer.toOctalString(runtime.getUmask()),
                      Integer.toOctalString(mode), perms);
        }
        return perms;
    }

    private void setModeNoPosix(Path p, int origMode)
    {
        File f = p.toFile();
        // We won't check the result of these calls. They don't all work
        // on all OSes, like Windows. If some fail, then we did the best
        // that we could to follow the request.
        int mode =
            origMode & (~(runtime.getUmask()));
        if (((mode & Constants.S_IROTH) != 0) || ((mode & Constants.S_IRGRP) != 0)) {
            f.setReadable(true, false);
        } else if ((mode & Constants.S_IRUSR) != 0) {
            f.setReadable(true, true);
        } else {
            f.setReadable(false, true);
        }

        if (((mode & Constants.S_IWOTH) != 0) || ((mode & Constants.S_IWGRP) != 0)) {
            f.setWritable(true, false);
        } else if ((mode & Constants.S_IWUSR) != 0) {
            f.setWritable(true, true);
        } else {
            f.setWritable(false, true);
        }

        if (((mode & Constants.S_IXOTH) != 0) || ((mode & Constants.S_IXGRP) != 0)) {
            f.setExecutable(true, false);
        } else if ((mode & Constants.S_IXUSR) != 0) {
            f.setExecutable(true, true);
        } else {
            f.setExecutable(false, true);
        }
    }

    private FileHandle ensureHandle(int fd)
        throws NodeOSException
    {
        FileHandle handle = descriptors.get(fd);
        if (handle == null) {
            if (log.isTraceEnabled()) {
                log.trace("File handle {} is not a regular file, fd");
            }
            throw new NodeOSException(Constants.EBADF);
        }
        return handle;
    }

    private FileHandle ensureRegularFileHandle(int fd)
        throws NodeOSException
    {
        FileHandle h = ensureHandle(fd);
        if (h.file == null) {
            if (Files.isDirectory(h.path)) {
                if (log.isTraceEnabled()) {
                    log.trace("File handle {} is a directory and not a regular file", fd);
                }
                throw new NodeOSException(Constants.EISDIR);
            }
            if (log.isTraceEnabled()) {
                log.trace("File handle {} is not a regular file", fd);
            }
            throw new NodeOSException(Constants.EBADF);
        }
        return h;
    }

    public static class FileHandle
    {
        static final String KEY = "_fileHandle";

        AsynchronousFileChannel file;
        Path path;
        long position;
        boolean noFollow;

        FileHandle(Path path, AsynchronousFileChannel file)
        {
            this.path = path;
            this.file = file;
        }
    }
}
