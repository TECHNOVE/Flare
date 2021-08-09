package co.technove.flare.internal.util;

import java.util.AbstractList;
import java.util.Arrays;

public class DoubleArrayList extends AbstractList<Double> {

    private double[] array;
    private int size = 0;

    public DoubleArrayList() {
        this(10);
    }

    public DoubleArrayList(int capacity) {
        this.array = new double[capacity];
    }

    @Override
    public Double get(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException(index);
        }
        return this.array[index];
    }

    @Override
    public Double remove(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException(index);
        }

        double removed = this.array[index];

        this.size--;
        System.arraycopy(this.array, index + 1, this.array, index, this.array.length - index);

        // resize as needed
        if (this.size < this.array.length >> 1) {
            this.array = Arrays.copyOfRange(this.array, 0, Math.max(10, this.array.length >> 1));
        }

        return removed;
    }

    public void addDouble(double element) {
        this.addDouble(this.size, element);
    }

    @Override
    public void add(int index, Double element) {
        this.addDouble(index, element);
    }

    public void addDouble(int index, double element) {
        if (index < 0 || index > this.size) {
            throw new IndexOutOfBoundsException(index);
        }

        if (this.size == this.array.length) {
            this.array = Arrays.copyOf(this.array, this.array.length << 1);
        }

        if (index == this.size) {
            this.array[this.size] = element;
            this.size++;
        } else {
            System.arraycopy(this.array, index, this.array, index + 1, this.array.length - index);
            this.array[index] = element;
        }
    }

    @Override
    public Double set(int index, Double element) {
        if (index < 0 || index > this.size) {
            throw new IndexOutOfBoundsException(index);
        }

        double previous = this.array[index];
        this.array[index] = element;
        return previous;
    }

    @Override
    public void clear() {
        int size = Math.max(10, this.array.length >> 1 > this.size ? this.array.length >> 1 : this.array.length);
        this.array = new double[size];
        this.size = 0;
    }

    @Override
    public int size() {
        return this.size;
    }
}
