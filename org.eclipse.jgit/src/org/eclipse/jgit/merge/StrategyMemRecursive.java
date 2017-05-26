package org.eclipse.jgit.merge;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;


public class StrategyMemRecursive extends StrategyRecursive{

    public static final StrategyRecursive MEM_RECURSIVE = new StrategyMemRecursive();

    static {
        register(MEM_RECURSIVE);
    }

    @Override
    public ThreeWayMerger newMerger(Repository db) {
        return new MemRecursiveMerger(db, true);
    }

    @Override
    public ThreeWayMerger newMerger(Repository db, boolean inCore) {
        return new MemRecursiveMerger(db, true);
    }

    @Override
    public ThreeWayMerger newMerger(ObjectInserter inserter, Config config) {
        return new MemRecursiveMerger(inserter, config);
    }

    @Override
    public String getName() {
        return "mem_recursive";
    }

}
