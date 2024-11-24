package org.evosuite.testcase.factories;

import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.testcase.DefaultTestCase;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFactory;
import org.evosuite.testcase.execution.ExecutionTracer;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于LLM生成测试用例的工厂类
 */
public class LLMBasedTestFactory implements ChromosomeFactory<TestChromosome> {

    private static final Logger logger = LoggerFactory.getLogger(LLMBasedTestFactory.class);

    private TestCase getLLMGuidedTestCase(int size) {
        boolean tracerEnabled = ExecutionTracer.isEnabled();
        if (tracerEnabled)
            ExecutionTracer.disable();

        final TestCase test = getNewTestCase();
        final TestFactory testFactory = TestFactory.getInstance();

        // Choose a random length between 1 (inclusive) and size (exclusive).
        final int length = Randomness.nextInt(1, size);

        // Then add random statements until the test case reaches the chosen length, or we run out of
        // generation attempts.
        for (int num = 0; test.size() < length && num < Properties.MAX_ATTEMPTS; num++)
            // NOTE: Even though extremely unlikely, insertLLMGuidedStatement could fail every time
            // with return code -1, thus eventually exceeding MAX_ATTEMPTS. In this case, the
            // returned test case would indeed be empty!
            testFactory.insertLLMGuidedStatement(test, test.size() - 1);

        if (logger.isDebugEnabled())
            logger.debug("LLM-guided test case:" + test.toCode());

        return test;
    }

    @Override
    public TestChromosome getChromosome() {
        TestChromosome c = new TestChromosome();
        c.setTestCase(getLLMGuidedTestCase(Properties.CHROMOSOME_LENGTH));
        return c;
    }

    private TestCase getNewTestCase() {
        return new DefaultTestCase();
    }
}
