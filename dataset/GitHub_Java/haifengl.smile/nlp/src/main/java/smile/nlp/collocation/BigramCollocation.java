/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.nlp.collocation;

/**
 * Collocations are expressions of multiple words which commonly co-occur.
 * A bigram collocation is a pair of words w1 w2 that appear together with
 * statistically significance.
 *
 * @author Haifeng Li
 */
public class BigramCollocation implements Comparable<BigramCollocation> {

    /**
     * The first word of bigram.
     */
    private String w1;
    /**
     * The second word of bigram.
     */
    private String w2;
    /**
     * The frequency of bigram in the corpus.
     */
    private int frequency;
    /**
     * The chi-square statistical score of the collocation.
     */
    private double score;

    /**
     * Constructor.
     * @param w1 the first word of bigram.
     * @param w2 the second word of bigram.
     * @param frequency the frequency of bigram in the corpus.
     * @param score the chi-square statistical score of collocation in a corpus.
     */
    public BigramCollocation(String w1, String w2, int frequency, double score) {
        this.w1 = w1;
        this.w2 = w2;
        this.frequency = frequency;
        this.score = score;
    }

    /**
     * Returns the first word of bigram.
     */
    public String w1() {
        return w1;
    }

    /**
     * Returns the second word of bigram.
     */
    public String w2() {
        return w2;
    }

    /**
     * Returns the frequency of bigram in the corpus.
     */
    public int frequency() {
        return frequency;
    }

    /**
     * Returns the chi-square statistical score of the collocation.
     */
    public double score() {
        return score;
    }

    @Override
    public String toString() {
        return String.format("(%s %s, %d, %.2f)", w1, w2, frequency, score);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + w1.hashCode();
        hash = 37 * hash + w2.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BigramCollocation other = (BigramCollocation) obj;
        if ((this.w1 == null) ? (other.w1 != null) : !this.w1.equals(other.w1)) {
            return false;
        }
        if ((this.w2 == null) ? (other.w2 != null) : !this.w2.equals(other.w2)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(BigramCollocation o) {
        return (int) Math.signum(score - o.score);
    }
}
