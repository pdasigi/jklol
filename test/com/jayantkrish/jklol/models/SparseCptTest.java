import com.jayantkrish.jklol.models.*;

import junit.framework.*;
import java.util.*;

/**
 * A test of SparseCpts and their interactions with CptTableFactors.
 */
public class SparseCptTest extends TestCase {

    private Cpt cpt;
    private SparseCpt sparse;
    private CptTableFactor f;
    private Variable<String> v;

    private Object[][] assignments;

    public void setUp() {
	v = new Variable<String>("Two values",
		Arrays.asList(new String[] {"T", "F"}));

	f = new CptTableFactor(Arrays.asList(new Integer[] {0, 1, 2, 3}), Arrays.asList(new Variable[] {v, v, v, v,}),
		Arrays.asList(new Integer[] {0, 1}), Arrays.asList(new Variable[] {v, v}),
		Arrays.asList(new Integer[] {2, 3}), Arrays.asList(new Variable[] {v, v}));

	sparse = new SparseCpt(Arrays.asList(new Variable[] {v, v}), Arrays.asList(new Variable[] {v, v}));
	
	Map<Integer, Integer> cptVarNumMap = new HashMap<Integer, Integer>();
	for (int i = 0; i < 4; i++) {
	    cptVarNumMap.put(i, i);
	}

	// Note: Parent F, T was unassigned!
	assignments = new Object[][] {{"T", "T", "T", "T"},
				      {"T", "T", "F", "T"},
				      {"T", "F", "F", "T"},
				      {"F", "F", "F", "F"},
				      {"F", "F", "T", "T"}};
	for (int i = 0; i < assignments.length; i++) {
	    sparse.setNonZeroProbabilityOutcome(f.outcomeToAssignment(assignments[i]));
	}
	f.setCpt(sparse, cptVarNumMap);
    }


    public void testSmoothing() {
	f.addUniformSmoothing(1.0);

	assertEquals(0.5, f.getUnnormalizedProbability(f.outcomeToAssignment(assignments[0])));
	assertEquals(0.0, f.getUnnormalizedProbability(f.outcomeToAssignment(new Object[] {"T", "T", "F", "F"})));
	assertEquals(1.0, f.getUnnormalizedProbability(f.outcomeToAssignment(assignments[2])));
    }

    public void testIteration() {
	Iterator<Assignment> iter = f.outcomeIterator();

	Set<Assignment> shouldBeInIter = new HashSet<Assignment>();
	for (int i = 0; i < assignments.length; i++) {
	    shouldBeInIter.add(f.outcomeToAssignment(assignments[i]));
	}
	
	while (iter.hasNext()) {
	    Assignment a = iter.next();
	    assertTrue(shouldBeInIter.contains(a));
	    // If this outcome isn't possible, this method will throw a runtime exception
	    f.getUnnormalizedProbability(a);
	}
    }

    public void testUnsetParentError() {
	try {
	    f.getUnnormalizedProbability(f.outcomeToAssignment(new Object[] {"F", "T", "T", "T"}));
	} catch (RuntimeException e) {
	    return;
	}
	fail("Expected RuntimeException");
    }
}