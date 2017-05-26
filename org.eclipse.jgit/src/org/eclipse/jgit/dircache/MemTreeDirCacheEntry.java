package org.eclipse.jgit.dircache;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;

/**
 * Created by liang on 2017/5/26.
 */
public class MemTreeDirCacheEntry extends DirCacheEntry{

    public MemTreeDirCacheEntry(final String newPath) {
        super(Constants.encode(newPath), STAGE_0);
    }

    @Override
    public void setFileMode(FileMode mode) {
        super.setFileMode(mode.getBits());
    }
}
