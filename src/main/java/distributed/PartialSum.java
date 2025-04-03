package distributed;

import java.util.List;

/**
 * PartialSum class to encapsulate a list of numbers and its sum
 * SER321 Assignment 5
 * 
 * @author tfilewic 
 * @version 2025-02-20
*/
public class PartialSum {
    private final List<Integer> numbers;
    private int sum;
    private int addedBy = 0;
    
    public PartialSum(List<Integer> numbers, int sum) {
        this.numbers = numbers;
        this.sum = sum;
    }
    
    public List<Integer> getNumbers() {
        return numbers;
    }
    
    public int getSum() {
        return sum;
    }
    
    public void setSum(int newSum) {
        sum = newSum;
    }
    
    public void setId(int id) {
        if (addedBy != 0 || id < 1) return;
        addedBy = id;
    }
    
    public int getId() {
        return addedBy;
    }
    
    //DEBUG
    @Override
    public String toString() {
        return "PartialSum [" + numbers + "] = " + sum;
    }
}