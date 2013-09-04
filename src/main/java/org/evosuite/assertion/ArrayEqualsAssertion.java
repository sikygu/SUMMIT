/**
 * 
 */
package org.evosuite.assertion;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.evosuite.testcase.CodeUnderTestException;
import org.evosuite.testcase.Scope;
import org.evosuite.testcase.TestCase;

/**
 * @author Gordon Fraser
 * 
 */
public class ArrayEqualsAssertion extends Assertion {

	private static final long serialVersionUID = -1868479914750970330L;

	/** {@inheritDoc} */
	@Override
	public Assertion copy(TestCase newTestCase, int offset) {
		EqualsAssertion s = new EqualsAssertion();
		s.source = source.copy(newTestCase, offset);
		s.value = value;
		return s;
	}

	/** {@inheritDoc} */
	@Override
	public String getCode() {
		return "assertArrayEquals(" + value + ", " + source.getName() + ");";
	}

	private Object[] getArray(Object val) {
		int arrlength = Array.getLength(val);
		Object[] outputArray = new Object[arrlength];
		for (int i = 0; i < arrlength; ++i) {
			outputArray[i] = Array.get(val, i);
		}
		return outputArray;
	}

	/* (non-Javadoc)
	 * @see org.evosuite.assertion.Assertion#evaluate(org.evosuite.testcase.Scope)
	 */
	@Override
	public boolean evaluate(Scope scope) {
		try {
			if (source.getObject(scope) == null)
				return value == null;
			else
				return Arrays.equals(getArray(source.getObject(scope)), (Object[]) value);
		} catch (CodeUnderTestException e) {
			throw new UnsupportedOperationException();
		}
	}

}
