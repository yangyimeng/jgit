package org.eclipse.jgit.dircache;


import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;

public class MemDeleteDirCacheEntry extends DirCacheEntry{

    public MemDeleteDirCacheEntry(final String newPath) {
        super(Constants.encode(newPath), STAGE_0);
    }

    @Override
    public void setFileMode(FileMode mode) {
        super.setFileMode(mode.getBits());
    }
}
