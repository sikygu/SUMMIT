package org.evosuite.ga.metaheuristics;

import org.evosuite.Properties;
import org.evosuite.TimeController;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.archive.Archive;
import org.evosuite.ga.metaheuristics.mosa.AbstractMOSA;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.factories.LLMBasedTestFactory;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the SUMMIT algorithm
 */
public class Summit extends AbstractMOSA {

    private static final Logger logger = LoggerFactory.getLogger(Summit.class);

    private final ChromosomeFactory<TestChromosome> llmFactory = new LLMBasedTestFactory();

    private double pr = Properties.P_RANDOM_TEST_OR_FROM_ARCHIVE;

    private int n = Properties.NUMBER_OF_TESTS_PER_TARGET;

    private TestChromosome solution = null;

    /**
     * Constructor.
     *
     * @param factory a {@link org.evosuite.ga.ChromosomeFactory} object.
     */
    public Summit(ChromosomeFactory<TestChromosome> factory) {
        super(factory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void evolve() {

        // From the second step on, SUMMIT will decide to either sample a new test using LLM
        // (probability Pr), or will choose one existing test in the archive (probability
        // 1 - Pr), copy it, and mutate it.
        //
        // Note: in SUMMIT there is an extra parameter m which controls how many mutations and
        // fitness evaluations should be done on the same individual before sampling a new
        // one.

        if (this.solution == null || (this.solution
                .getNumberOfMutations() >= Properties.MAX_NUM_MUTATIONS_BEFORE_GIVING_UP
                || this.solution
                .getNumberOfEvaluations() >= Properties.MAX_NUM_FITNESS_EVALUATIONS_BEFORE_GIVING_UP)) {

            TestChromosome test = null;
            if (Randomness.nextDouble() < this.pr) {
                test = this.llmFactory.getChromosome();
                if (test.size() == 0) {
                    // in case EvoSuite fails to generate a new test
                    // case, get one from the archive
                    test = Archive.getArchiveInstance().getSolution();
                }
            } else {
                test = Archive.getArchiveInstance().getSolution();
                if (test == null || test.size() == 0) {
                    test = this.llmFactory.getChromosome();
                }
            }
            assert test != null && test.size() != 0;
            this.solution = test;
        }
        assert this.solution != null;

        // mutate it
        notifyMutation(this.solution);
        this.solution.mutate();

        // evaluate it
        this.calculateFitness(this.solution);

        double usedBudget = this.progress();
        if (Double.compare(usedBudget, Properties.EXPLOITATION_STARTS_AT_PERCENT) >= 0) {
            // focused search has started
            this.pr = 0.0;
            this.n = 1;
        } else {
            double scale = usedBudget / Properties.EXPLOITATION_STARTS_AT_PERCENT;
            this.pr = Properties.P_RANDOM_TEST_OR_FROM_ARCHIVE
                    - (scale * Properties.P_RANDOM_TEST_OR_FROM_ARCHIVE);
            this.n = (int) Math.ceil(
                    Properties.NUMBER_OF_TESTS_PER_TARGET - (scale * Properties.NUMBER_OF_TESTS_PER_TARGET));

            logger.debug("usedBudget: " + usedBudget + " | scale: " + scale + " | Pr: " + this.pr
                    + " | N: " + this.n);
        }

        assert this.pr >= 0.0;
        assert this.n >= 1;
        Archive.getArchiveInstance().shrinkSolutions(this.n);

        this.currentIteration++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializePopulation() {
        this.notifySearchStarted();
        this.currentIteration = 0;

        logger.debug("Set up initial population of size one");
        // At the beginning of the search, the archive will be empty, and so a new test
        // will be randomly generated.
        this.generateInitialPopulation(1);
        assert this.population.size() == 1;
        this.solution = this.population.get(0).clone();

        // update fitness values of all individuals
        this.calculateFitnessAndSortPopulation();

        this.notifyIteration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generateSolution() {
        if (this.population.isEmpty()) {
            this.initializePopulation();
            assert !this.population
                    .isEmpty() : "Initial population is empty, i.e., EvoSuite could not create any test!";
        }

        if (Properties.ENABLE_SECONDARY_OBJECTIVE_AFTER > 0
                || Properties.ENABLE_SECONDARY_OBJECTIVE_STARVATION) {
            this.disableFirstSecondaryCriterion();
        }

        logger.debug("Starting evolution");
        while (!this.isFinished()) {
            this.evolve();

            if (this.shouldApplyLocalSearch()) {
                TestSuiteChromosome testSuite = new TestSuiteChromosome();

                // local search process only take into account the population of the GA, and not the
                // solutions in the archive
                if (Archive.getArchiveInstance().hasBeenUpdated()) {
                    Set<TestChromosome> testsInArchive = Archive.getArchiveInstance().getSolutions();
                    if (!testsInArchive.isEmpty()) {
                        for (TestChromosome test : testsInArchive) {
                            testSuite.addTest(test.getTestCase().clone());
                        }
                    }
                }

                this.applyLocalSearch(testSuite);
            }

            logger.info("Updating fitness values");
            this.updateFitnessFunctionsAndValues();

            logger.info("Current iteration: " + currentIteration);
            this.notifyIteration();
        }

        TimeController.execute(this::updateBestIndividualFromArchive, "Update from archive", 5_000);
        this.notifySearchFinished();
    }

    @Override
    public List<TestChromosome> getBestIndividuals() {
        return new ArrayList<>(Archive.getArchiveInstance().getSolutions());
    }
}
