package org.eclipse.jgit.merge;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Theories.class)
public class MemRecursiveMergerTest extends RepositoryTestCase {

    private TestRepository<FileRepository> db_t;

    public static ThreeWayMergeStrategy memRecursive = StrategyMemRecursive.MEM_RECURSIVE;
    public static ThreeWayMergeStrategy recursive = MergeStrategy.RECURSIVE;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        db_t = new TestRepository<>(db);
    }


    @Test
    public void checkMergeEqualTreesInCore_noRepo()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("d/1", "orig");
        git.add().addFilepattern("d/1").call();
        RevCommit first = git.commit().setMessage("added d/1").call();

        writeTrashFile("d/1", "modified");
        RevCommit masterCommit = git.commit().setAll(true)
                .setMessage("modified d/1 on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("d/2", "modified");
        git.add().addFilepattern("d/2").call();
        RevCommit sideCommit = git.commit().setAll(true)
                .setMessage("modified d/1 on side").call();

        git.rm().addFilepattern("d/1").call();
        git.rm().addFilepattern("d").call();
        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    @Test
    /***
     * merge repository with plenty of file
     */
    public void testLargeRepositoryMerge() throws Exception{
        Git git = new Git(db);
        for (int i = 0; i < 100000; i++) {
            writeTrashFile("a/" + i, i + "");
        }
        for (int i = 0; i < 100000; i++) {
            writeTrashFile("b/" + i, i + "");
        }
        for (int i = 0; i < 100000; i++) {
            writeTrashFile("c/" + i, i + "");
        }
        git.add().addFilepattern(".").call();
        RevCommit first = git.commit().setMessage("first commit").call();
        writeTrashFile("d/1", "1");
        git.add().addFilepattern("d/1").call();
        RevCommit masterCommit = git.commit().setAll(true).
                setMessage("master commit").call();
        git.checkout().setCreateBranch(true).setStartPoint(first).
                setName("size").call();
        writeTrashFile("d/2", "2");
        git.add().addFilepattern("d/2").call();
        RevCommit sideCommit = git.commit().setMessage("size commit").call();
        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    /**
     * Merging two conflicting subtrees when the index and HEAD does not contain
     * any file in that subtree should lead to a conflicting state.
     *
     * @throws Exception
     */
    @Test
    public void checkMergeConflictingNewTrees()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("2", "orig");
        git.add().addFilepattern("2").call();
        RevCommit first = git.commit().setMessage("added 2").call();

        writeTrashFile("d/1", "master");
        git.add().addFilepattern("d/1").call();
        RevCommit masterCommit = git.commit().setAll(true)
                .setMessage("added d/1 on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("d/1", "side");
        git.add().addFilepattern("d/1").call();
        RevCommit sideCommit = git.commit().setAll(true).setMessage("added d/1 on side").call();

        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    /**
     * Merging two conflicting files when the index contains a tree for that
     * path should lead to a failed state.
     *
     * @throws Exception
     */
    @Test
    public void checkMergeConflictingFilesWithTreeInIndex()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("0", "orig");
        git.add().addFilepattern("0").call();
        RevCommit first = git.commit().setMessage("added 0").call();

        writeTrashFile("0", "master");
        RevCommit masterCommit = git.commit().setAll(true)
                .setMessage("modified 0 on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("0", "side");
        RevCommit sideCommit = git.commit().setAll(true).setMessage("modified 0 on side").call();
        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }

    /**
     * Merging two equal files when the index contains a tree for that path
     * should lead to a failed state.
     *
     * @throws Exception
     */
    @Test
    public void checkMergeMergeableFilesWithTreeInIndex()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("0", "orig");
        writeTrashFile("1", "1\n2\n3");
        git.add().addFilepattern("0").addFilepattern("1").call();
        RevCommit first = git.commit().setMessage("added 0, 1").call();

        writeTrashFile("1", "1master\n2\n3");
        RevCommit masterCommit = git.commit().setAll(true)
                .setMessage("modified 1 on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("1", "1\n2\n3side");
        RevCommit sideCommit = git.commit().setAll(true).setMessage("modified 1 on side").call();
        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    @Test
    public void checkContentMergeNoConflict()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("file", "1\n2\n3");
        git.add().addFilepattern("file").call();
        RevCommit first = git.commit().setMessage("added file").call();

        writeTrashFile("file", "1master\n2\n3");
        RevCommit masterCommit = git.commit().setAll(true).setMessage("modified file on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("file", "1\n2\n3side");
        RevCommit sideCommit = git.commit().setAll(true)
                .setMessage("modified file on side").call();

        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    @Test
    public void checkContentMergeNoConflict_noRepo()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("file", "1\n2\n3");
        git.add().addFilepattern("file").call();
        RevCommit first = git.commit().setMessage("added file").call();

        writeTrashFile("file", "1master\n2\n3");
        RevCommit masterCommit = git.commit().setAll(true)
                .setMessage("modified file on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("file", "1\n2\n3side");
        RevCommit sideCommit = git.commit().setAll(true)
                .setMessage("modified file on side").call();

        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    @Test
    public void checkContentMergeConflict()
            throws Exception {
        Git git = Git.wrap(db);

        writeTrashFile("file", "1\n2\n3");
        git.add().addFilepattern("file").call();
        RevCommit first = git.commit().setMessage("added file").call();

        writeTrashFile("file", "1master\n2\n3");
        RevCommit masterCommit = git.commit().setAll(true).setMessage("modified file on master").call();

        git.checkout().setCreateBranch(true).setStartPoint(first)
                .setName("side").call();
        writeTrashFile("file", "1side\n2\n3");
        RevCommit sideCommit = git.commit().setAll(true)
                .setMessage("modified file on side").call();
        assertEqualMerge(memRecursive, recursive, masterCommit, sideCommit);
    }


    /***
     * compare method to verify if new merge alg is right
     * @param strategyRecursive1
     * @param strategyRecursive2
     * @param master
     * @param merge
     * @throws Exception
     */
    public void assertEqualMerge(ThreeWayMergeStrategy strategyRecursive1, ThreeWayMergeStrategy strategyRecursive2, RevCommit master, RevCommit merge) throws Exception{
        ThreeWayMerger threeWayMerger1 = strategyRecursive1.newMerger(db, true);
        long start = System.currentTimeMillis();
        boolean noProblems1 = threeWayMerger1.merge(master, merge);
        long end = System.currentTimeMillis();
        System.out.println(String.format("%s alg coust %d", strategyRecursive1.getName(), end - start));
        ThreeWayMerger threeWayMerger2 = strategyRecursive2.newMerger(db, true);
        start = System.currentTimeMillis();
        boolean noProblems2 = threeWayMerger2.merge(master, merge);
        end = System.currentTimeMillis();
        System.out.println(String.format("%s alg coust %d", strategyRecursive2.getName(), end - start));
        assertTrue(noProblems1 == noProblems2);
        if (noProblems1) {
            assertTrue(threeWayMerger1.getResultTreeId().equals(threeWayMerger2.getResultTreeId()));
        } else {
            ResolveMerger resolveMerger1 = (ResolveMerger) threeWayMerger1;
            ResolveMerger resolveMerger2 = (ResolveMerger) threeWayMerger2;
            List<String> unMergedPaths1 = resolveMerger1.getUnmergedPaths();
            List<String> unMergedPaths2 = resolveMerger2.getUnmergedPaths();
            assertTrue(unMergedPaths1.size() == unMergedPaths2.size());
            Collections.sort(unMergedPaths1);
            Collections.sort(unMergedPaths2);
            for (int i = 0; i < resolveMerger1.getUnmergedPaths().size(); i++) {
                assertTrue(unMergedPaths1.get(i).equals(unMergedPaths2.get(i)));
            }
        }
    }


}
