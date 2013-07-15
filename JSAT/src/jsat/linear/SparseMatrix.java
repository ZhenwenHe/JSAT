package jsat.linear;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import static jsat.linear.Matrix.canMultiply;

/**
 * Creates a new Sparse Matrix where each row is backed by a sparse vector. 
 * <br><br>
 * This implementation does not support the {@link #qr() QR} or {@link #lup() } 
 * decompositions. 
 * <br>
 * {@link #transposeMultiply(jsat.linear.Matrix, jsat.linear.Matrix, java.util.concurrent.ExecutorService) } currently does not use multiple cores. 
 * 
 * @author Edward Raff
 */
public class SparseMatrix extends Matrix
{
    private SparseVector[] rows;

    /**
     * Creates a new sparse matrix
     * @param rows the number of rows for the matrix
     * @param cols the number of columns for the matrix
     * @param rowCapacity the initial capacity for non zero values for each row
     */
    public SparseMatrix(int rows, int cols, int rowCapacity)
    {
        this.rows = new SparseVector[rows];
        for(int i = 0; i < rows; i++)
            this.rows[i] = new SparseVector(cols, rowCapacity);
    }
    
    /**
     * Creates a new sparse matrix
     * @param rows the number of rows for the matrix
     * @param cols the number of columns for the matrix
     */
    public SparseMatrix(int rows, int cols)
    {
        this.rows = new SparseVector[rows];
        for(int i = 0; i < rows; i++)
            this.rows[i] = new SparseVector(cols);
    }
    /**
     * Copy constructor
     * @param toCopy the object to copy
     */
    protected SparseMatrix(SparseMatrix toCopy)
    {
        this.rows = new SparseVector[toCopy.rows.length];
        for(int i = 0; i < rows.length; i++)
            this.rows[i] = toCopy.rows[i].clone();
    }

    @Override
    public void mutableAdd(double c, Matrix B)
    {
        if(!Matrix.sameDimensions(this, B))
            throw new ArithmeticException("Matrices must be the same dimension to be added");
        for( int i = 0; i < rows.length; i++)
            rows[i].mutableAdd(c, B.getRowView(i));
    }

    @Override
    public void mutableAdd(final double c, final Matrix B, ExecutorService threadPool)
    {
        if(!Matrix.sameDimensions(this, B))
            throw new ArithmeticException("Matrices must be the same dimension to be added");
        
        final CountDownLatch latch = new CountDownLatch(rows.length);
        for (int i = 0; i < rows.length; i++)
        {
            final int ii = i;
            threadPool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    rows[ii].mutableAdd(c, B.getRowView(ii));
                    latch.countDown();
                }
            });
        }
        try
        {
            latch.await();
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(SparseMatrix.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void mutableAdd(double c)
    {
        for(SparseVector row : rows)
            row.mutableAdd(c);
    }

    @Override
    public void mutableAdd(final double c, ExecutorService threadPool)
    {
        final CountDownLatch latch = new CountDownLatch(rows.length);
        for(final SparseVector row : rows)
        {
            threadPool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    row.mutableAdd(c);
                    latch.countDown();
                }
            });
        }
        try
        {
            latch.await();
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(SparseMatrix.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void multiply(Vec b, double z, Vec c)
    {
        if(this.cols() != b.length())
            throw new ArithmeticException("Matrix dimensions do not agree, [" + rows() +"," + cols() + "] x [" + b.length() + ",1]" );
        if(this.rows() != c.length())
            throw new ArithmeticException("Target vector dimension does not agree with matrix dimensions. Matrix has " + rows() + " rows but tagert has " + c.length());
        
        for(int i = 0; i < rows(); i++)
        {
            SparseVector row = rows[i];
            c.increment(i, row.dot(b)*z);
        }
    }

    @Override
    public void multiply(Matrix B, Matrix C)
    {
        if(!canMultiply(this, B))
            throw new ArithmeticException("Matrix dimensions do not agree");
        else if(this.rows() != C.rows() || B.cols() != C.cols())
            throw new ArithmeticException("Target Matrix is no the correct size");
        
        for (int i = 0; i < C.rows(); i++)
        {
            Vec Arowi = this.rows[i];
            Vec Crowi = C.getRowView(i);

            for(IndexValue iv : Arowi)
            {
                final int k = iv.getIndex();
                double a = iv.getValue();
                Vec Browk = B.getRowView(k);
                Crowi.mutableAdd(a, Browk);
            }
        }
    }

    @Override
    public void multiply(final Matrix B, Matrix C, ExecutorService threadPool)
    {
        if (!canMultiply(this, B))
            throw new ArithmeticException("Matrix dimensions do not agree");
        else if (this.rows() != C.rows() || B.cols() != C.cols())
            throw new ArithmeticException("Target Matrix is no the correct size");

        final CountDownLatch latch = new CountDownLatch(C.rows());
        for (int i = 0; i < C.rows(); i++)
        {
            final Vec Arowi = this.rows[i];
            final Vec Crowi = C.getRowView(i);

            threadPool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    for (IndexValue iv : Arowi)
                    {
                        final int k = iv.getIndex();
                        double a = iv.getValue();
                        Vec Browk = B.getRowView(k);
                        Crowi.mutableAdd(a, Browk);
                    }
                    
                    latch.countDown();
                }
            });
        }
        try
        {
            latch.await();
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(SparseMatrix.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void mutableMultiply(double c)
    {
        for(SparseVector row : rows)
            row.mutableMultiply(c);
    }

    @Override
    public void mutableMultiply(final double c, ExecutorService threadPool)
    {
        final CountDownLatch latch = new CountDownLatch(rows.length);
        for(final SparseVector row : rows)
        {
            threadPool.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    row.mutableMultiply(c);
                    latch.countDown();
                }
            });
        }
        try
        {
            latch.await();
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(SparseMatrix.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public Matrix[] lup()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Matrix[] lup(ExecutorService threadPool)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Matrix[] qr()
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Matrix[] qr(ExecutorService threadPool)
    {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void mutableTranspose()
    {
        for(int i = 0; i < rows()-1; i++)
            for(int j = i+1; j < cols(); j++)
            {
                double tmp = get(j, i);
                set(j, i, get(i, j));
                set(i, j, tmp);
            }
    }

    @Override
    public void transpose(Matrix C)
    {
        if(this.rows() != C.cols() || this.cols() != C.rows())
            throw new ArithmeticException("Target matrix does not have the correct dimensions");
        
        C.zeroOut();
        for(int row = 0; row < rows.length; row++)
            for(IndexValue iv : rows[row])
                C.set(iv.getIndex(), row, iv.getValue());
    }

    @Override
    public void transposeMultiply(Matrix B, Matrix C)
    {
        if(this.rows() != B.rows())//Normaly it is A_cols == B_rows, but we are doint A'*B, not A*B
            throw new ArithmeticException("Matrix dimensions do not agree");
        else if(this.cols() != C.rows() || B.cols() != C.cols())
            throw new ArithmeticException("Destination matrix does not have matching dimensions");
        final SparseMatrix A = this;
        ///Should choose step size such that 2*NB2^2 * dataTypeSize <= CacheSize
        
        final int kLimit = this.rows();

        for (int k = 0; k < kLimit; k++)
        {
            Vec bRow_k = B.getRowView(k);
            Vec aRow_k = A.getRowView(k);

            for (IndexValue iv : aRow_k)//iterating over "i"
            {

                Vec cRow_i = C.getRowView(iv.getIndex());
                double a = iv.getValue();//A.get(k, i);

                cRow_i.mutableAdd(a, bRow_k);
            }
        }
    }

    @Override
    public void transposeMultiply(final Matrix B, final Matrix C, ExecutorService threadPool)
    {
        transposeMultiply(B, C);//TODO use the multiple threads
    }

    @Override
    public void transposeMultiply(double c, Vec b, Vec x)
    {
        if(this.rows() != b.length())
            throw new ArithmeticException("Matrix dimensions do not agree, [" + cols() +"," + rows() + "] x [" + b.length() + ",1]" );
        else if(this.cols() != x.length())
            throw new ArithmeticException("Matrix dimensions do not agree with target vector");
        
        for(IndexValue b_iv : b)
            x.mutableAdd(c*b_iv.getValue(), rows[b_iv.getIndex()]);
    }

    @Override
    public Vec getRowView(int r)
    {
        return rows[r];
    }

    @Override
    public double get(int i, int j)
    {
        return rows[i].get(j);
    }

    @Override
    public void set(int i, int j, double value)
    {
        rows[i].set(j, value);
    }

    @Override
    public void increment(int i, int j, double value)
    {
        rows[i].increment(j, value);
    }

    @Override
    public int rows()
    {
        return rows.length;
    }

    @Override
    public int cols()
    {
        return rows[0].length();
    }

    @Override
    public boolean isSparce()
    {
        return true;
    }

    @Override
    public void swapRows(int r1, int r2)
    {
        SparseVector tmp = rows[r2];
        rows[r2] = rows[r1];
        rows[r1] = tmp;
    }

    @Override
    public void zeroOut()
    {
        for(Vec row : rows)
            row.zeroOut();
    }

    @Override
    public SparseMatrix clone()
    {
        return new SparseMatrix(this);
    }

    @Override
    public long nnz()
    {
        int nnz = 0;
        for(Vec v : rows)
            nnz += v.nnz();
        return nnz;
    }

}