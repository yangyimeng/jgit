package org.eclipse.jgit.dircache;


import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MemRecursiveMerger;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.Paths;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class MemDirCache extends DirCache{

    private Repository repository;
    private MemRecursiveMerger memRecursiveMerger;
    private final String emptyTreeId = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";


    /***
     *
     * @param indexLocation
     * @param fs
     * @param repository
     * @param memRecursiveMerger
     */
    public MemDirCache(File indexLocation, FS fs, Repository repository, MemRecursiveMerger memRecursiveMerger) {
        super(indexLocation, fs);
        this.repository = repository;
        this.memRecursiveMerger = memRecursiveMerger;
    }


    /***
     *
     * @param repository
     * @param memRecursiveMerger
     * @return
     */
    public static DirCache newIncore(Repository repository, MemRecursiveMerger memRecursiveMerger) {
        return new MemDirCache(null, null, repository, memRecursiveMerger);
    }

    @Override
    public ObjectId writeTree(ObjectInserter ow) throws IOException {
        //TODO
        Map<String, Map> deltaChangeMaps = new HashMap<>();
        Map<String, DirCacheEntry> dirCacheEntryMap = new HashMap<>();
        deltaChangeMaps.put("/", new HashMap());
        int entryCount = getEntryCount();
        for (int i = 0; i < entryCount; i++) {
            DirCacheEntry dirCacheEntry = getEntry(i);
            dirCacheEntryMap.put(dirCacheEntry.getPathString(), dirCacheEntry);
            String[] paths = dirCacheEntry.getPathString().split("/");
            Map<String, Map> preMaps = deltaChangeMaps.get("/");
            for (String path : paths) {
                Map<String, Map> cur = preMaps.get(path);
                if (null == cur) {
                    cur = new HashMap<>();
                    preMaps.put(path, cur);
                }
                preMaps = cur;
            }
        }
        RevTree revTree = memRecursiveMerger.getHeadCommit().getTree();
        ObjectId treeId = recurseWriteTree(ow, dirCacheEntryMap, deltaChangeMaps, "/", "", revTree);
        return treeId;
    }


    public ObjectId recurseWriteTree(ObjectInserter ow, Map<String, DirCacheEntry> dirCacheEntryMap, Map<String, Map> deltaChangesMap,
                                     String curEntry, String curPath, AnyObjectId curTree) {
        ObjectId treeId = ObjectId.zeroId();
        //new items to generate tree object
        Map<String, FileEntry> newTreeItems = new HashMap<>();
        try {
            if (curTree != null) {
                CanonicalTreeParser canonicalTreeParser = new CanonicalTreeParser(null, repository.newObjectReader(), curTree);
                while (!canonicalTreeParser.eof()) {
                    byte[] value = new byte[20];
                    FileMode fileMode = canonicalTreeParser.getEntryFileMode();
                    ObjectId objectId = canonicalTreeParser.getEntryObjectId();
                    String name = canonicalTreeParser.getEntryPathString();
                    objectId.copyRawTo(value, 0);
                    FileEntry fileEntry = new FileEntry(name, fileMode, value);
                    newTreeItems.put(name, fileEntry);
                    canonicalTreeParser.next();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //get delta change
        //and replace canonical tree element to generate new tree object
        Map<String, Map> childEntryMap = deltaChangesMap.get(curEntry);
        for (String childEntry : childEntryMap.keySet()) {
            ObjectId objectId;
            FileMode fileMode;
            byte[] value = new byte[20];
            Map<String, Map> childEntryChildMap = childEntryMap.get(childEntry);
            if (childEntryChildMap != null && !childEntryChildMap.isEmpty()) {
                AnyObjectId childTree = null;
                FileEntry preFileEntry = newTreeItems.get(childEntry);
                if (preFileEntry != null) {
                    childTree = ObjectId.fromRaw(preFileEntry.getIdBuffer());
                }
                //if tree object
                //need to recurse this tree object to get tree objectId
                objectId = recurseWriteTree(ow, dirCacheEntryMap, childEntryMap, childEntry,
                        curPath + (curPath.equals("") ? "" : "/") + childEntry, childTree);
                fileMode = FileMode.TREE;
            } else {
                DirCacheEntry dirCacheEntry = dirCacheEntryMap.get(curPath + (curPath.equals("") ? "" : "/") + childEntry);
                if (dirCacheEntry instanceof MemDeleteDirCacheEntry) {
                    //remove entry
                    if (newTreeItems.containsKey(childEntry)) {
                        newTreeItems.remove(childEntry);
                    }
                    continue;
                }
                objectId = dirCacheEntry.getObjectId();
                fileMode = dirCacheEntry.getFileMode();
            }
            objectId.copyRawTo(value, 0);
            FileEntry fileEntry = new FileEntry(childEntry, fileMode, value);

            //replace with delta change
            if (!objectId.getName().equals(emptyTreeId)) {
                newTreeItems.put(childEntry, fileEntry);
            } else {
                newTreeItems.remove(childEntry);
            }
        }

//    if (newTreeItems.isEmpty())
//      return treeId;
        TreeFormatter treeFormatter = new TreeFormatter();

        //sort all entry in git tree object order
        ArrayList<FileEntry> arrayList = new ArrayList<>();
        for (FileEntry fileEntry : newTreeItems.values()) {
            arrayList.add(fileEntry);
        }
        arrayList.sort(ENTRY_CMP);

        for (FileEntry fileEntry : arrayList) {
            treeFormatter.append(fileEntry.getEncodedName(), FileMode.fromBits(fileEntry.getFileMode()),
                    ObjectId.fromRaw(fileEntry.getIdBuffer()));
        }

        try {
            treeId = ow.insert(treeFormatter);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return treeId;
    }


    class FileEntry implements Comparable {
        private String filePath;
        private int fileMode;
        private boolean valid;
        private byte[] idBuffer;
        private int stage;

        public FileEntry(String path, FileMode mode, byte[] idBuffer) {
            this(path, mode, idBuffer, 0);
        }

        public FileEntry(String path, FileMode mode, byte[] idBuffer, int stage) {
            this(path, mode, idBuffer, stage, true);
        }

        public FileEntry(String path, FileMode mode, byte[] idBuffer, int stage, boolean valid) {
            filePath = path;
            fileMode = mode.getBits();
            this.idBuffer = idBuffer;
            this.stage = stage;
            this.valid = valid;
        }

        public void reset(FileMode mode, byte[] idBuffer, boolean valid) {
            fileMode = mode.getBits();
            this.idBuffer = idBuffer;
            this.valid = valid;
        }

        public int getStage() {
            return stage;
        }

        public byte[] getIdBuffer() {
            return idBuffer;
        }

        public byte[] getEncodedName() {
            return filePath.getBytes();
        }

        public int getEncodedNameLen() {
            return filePath.length();
        }

        public int getFileMode() {
            return fileMode;
        }

        public boolean isValid() {
            return valid;
        }

        public String getFilePath() {
            return filePath;
        }

        @Override
        public int compareTo(Object o) {
            FileEntry to = (FileEntry) o;
            byte[] fromPath = getEncodedName();
            int fromPathLen = getEncodedNameLen();
            byte[] toPath = to.getEncodedName();
            int toPathLen = to.getEncodedNameLen();

            for (int cPos = 0; cPos < fromPathLen && cPos < toPathLen; cPos++) {
                final int cmp = (fromPath[cPos] & 0xff) - (toPath[cPos] & 0xff);
                if (cmp != 0)
                    return cmp;
            }
            int pathDiff = fromPathLen - toPathLen;
            if (pathDiff == 0) {
                return stage - to.getStage();
            }
            return pathDiff;
        }
    }

    private static final Comparator<FileEntry> ENTRY_CMP = new Comparator<FileEntry>() {
        public int compare(FileEntry a, FileEntry b) {
            return Paths.compare(
                    a.getEncodedName(), 0, a.getEncodedNameLen(), a.getFileMode(),
                    b.getEncodedName(), 0, b.getEncodedNameLen(), b.getFileMode());
        }
    };


}
