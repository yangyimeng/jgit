package org.eclipse.jgit.merge;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Theories.class)
public class MemRecursiveMergerTest extends RepositoryTestCase {

    private TestRepository<FileRepository> db_t;

    public static MergeStrategy memRecursive = StrategyMemRecursive.MEM_RECURSIVE;
    public static MergeStrategy recursive = MergeStrategy.RECURSIVE;

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

        ThreeWayMerger memRecursiveMerger =
                (ThreeWayMerger) memRecursive.newMerger(db, true);
        boolean noProblems = memRecursiveMerger.merge(masterCommit, sideCommit);
        assertTrue(noProblems);

        ThreeWayMerger recursiveMerger = (ThreeWayMerger) recursive.newMerger(db, true);
        noProblems = recursiveMerger.merge(masterCommit, sideCommit);
        assertTrue(noProblems);

        assertTrue(memRecursiveMerger.getResultTreeId().equals(recursiveMerger.getResultTreeId()));

    }


    @Test
    public void testLargeRepositoryMerge() throws Exception{
        db = new FileRepository("/tmp/tmp_repository/.git");
        Git git = Git.wrap(db);
//        for (int i = 0; i < 100000; i++) {
//            writeTrashFile("a/" + i, i + "");
//        }
//        for (int i = 0; i < 100000; i++) {
//            writeTrashFile("b/" + i, i + "");
//        }
//        for (int i = 0; i < 100000; i++) {
//            writeTrashFile("c/" + i, i + "");
//        }
//        git.add().addFilepattern(".").call();
//        RevCommit first = git.commit().setMessage("first commit").call();
//        writeTrashFile("d/1", "1");
//        git.add().addFilepattern("d/1").call();
//        RevCommit masterCommit = git.commit().setAll(true).
//                setMessage("master commit").call();
//        git.checkout().setCreateBranch(true).setStartPoint(first).
//                setName("size").call();
//        writeTrashFile("d/2", "2");
//        git.add().addFilepattern("d/2").call();
//        RevCommit sideCommit = git.commit().setMessage("size commit").call();

        RevCommit masterCommit = db.parseCommit(ObjectId.fromString("5ebf0271da1749690822505f711d29800803db27"));
        RevCommit sideCommit = db.parseCommit(ObjectId.fromString("1910342f29ea01f7986b30ed7a4f6a0d4649e994"));
        long start = System.currentTimeMillis();
        ThreeWayMerger memRecursiveMerger =
                (ThreeWayMerger) memRecursive.newMerger(db, true);
        boolean noProblems = memRecursiveMerger.merge(masterCommit, sideCommit);
        long end = System.currentTimeMillis();
        System.out.println(end - start);
        assertTrue(noProblems);

        start = System.currentTimeMillis();
        ThreeWayMerger recursiveMerger = (ThreeWayMerger) recursive.newMerger(db, true);
        noProblems = recursiveMerger.merge(masterCommit, sideCommit);
        end = System.currentTimeMillis();
        System.out.println(end - start);
        assertTrue(noProblems);

        assertTrue(memRecursiveMerger.getResultTreeId().equals(recursiveMerger.getResultTreeId()));
    }




}
