package org.eclipse.jgit.merge;


import org.eclipse.jgit.dircache.*;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

import java.io.IOException;

public class MemRecursiveMerger extends RecursiveMerger{


    public MemRecursiveMerger(Repository local, boolean inCore) {
        super(local, inCore);
        if (inCore) {
            dircache = MemDirCache.newIncore(local, this);
        }
    }

    public MemRecursiveMerger(Repository local) {
        super(local, true);
    }

    public MemRecursiveMerger(ObjectInserter inserter, Config config){
        super(inserter, config);
    }

    public boolean isTree(final int mode) {
        return mode != 0 && FileMode.TREE.equals(mode);
    }

    @Override
    protected boolean processEntry(CanonicalTreeParser base, CanonicalTreeParser ours, CanonicalTreeParser theirs, DirCacheBuildIterator index, WorkingTreeIterator work, boolean ignoreConflicts) throws IOException {
        boolean result =  super.processEntry(base, ours, theirs, index, work, ignoreConflicts);
        //TODO
        //when merge without dircache, merge should pass directory when directory md5 is the same
        final int modeO = tw.getRawMode(T_OURS);
        final int modeT = tw.getRawMode(T_THEIRS);
        final int modeB = tw.getRawMode(T_BASE);
        //only process when below conditions is satisfied


        //all are tree object
        //no worktree
        //no dircache
        if (isTree(modeO) && isTree(modeT) && isTree(modeB) && work == null && index == null) {
            if (tw.idEqual(T_OURS, T_BASE) && tw.idEqual(T_OURS, T_THEIRS)) {
                enterSubtree = false;
            }
        }

        //base and ours is empty
        //theirs is tree
        //directly use their tree
        if (modeB == 0 && modeO == 0 && isTree(modeT) && work == null && index == null) {
            DirCacheEntry dirCacheEntry = new MemTreeDirCacheEntry(theirs.getEntryPathString());
            dirCacheEntry.setFileMode(theirs.getEntryFileMode());
            dirCacheEntry.setObjectId(theirs.getEntryObjectId());
            builder.add(dirCacheEntry);
            enterSubtree = false;
        }

        //condition one
        //base and their is empty
        //ours is tree
        //
        //condition two
        //base is empty
        //ours and theirs is tree and same
        //
        //use tree object directly
        try {
            if ((modeB == 0 && modeT == 0 && isTree(modeO) && work == null && index == null) ||
                    (modeB == 0 && isTree(modeO) && isTree(modeT) && tw.idEqual(T_OURS, T_THEIRS) && work == null && index == null)) {
                enterSubtree = false;
            }
        } catch (Exception ex) {
            System.out.println("haha");
        }

        return result;
    }

    public RevCommit getHeadCommit() {
        return sourceCommits[0];
    }

    @Override
    protected boolean mergeTreeWalk(TreeWalk treeWalk, boolean ignoreConflicts) throws IOException {
        boolean result = super.mergeTreeWalk(treeWalk, ignoreConflicts);
        //add all delete entry
        Repository repository = getRepository();
        RevWalk revWalk = new RevWalk(repository);
        for (String deleteEntry : toBeDeleted) {
            ObjectId objectId = repository.resolve(String.format("%s:%s", sourceTrees[0].getName(), deleteEntry));
            if (objectId == null || objectId.equals(ObjectId.zeroId())) {
                continue;
            }
            RevObject revObject = revWalk.parseAny(objectId);
            //only delete object that type is blob
            if (revObject instanceof RevBlob) {
                DirCacheEntry dirCacheEntry = new MemDeleteDirCacheEntry(deleteEntry);
                dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
                builder.add(dirCacheEntry);
            }
        }
        return result;
    }

}
