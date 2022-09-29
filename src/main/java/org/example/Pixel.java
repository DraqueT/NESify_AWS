package org.example;

/**
*
* @author draque
*/
public class Pixel implements Comparable<Pixel> {
       public final int r;
       public final int g;
       public final int b;
       public final int totalLuminosity;
       
       public Pixel(int _r, int _g, int _b) {
           r = _r;
           g = _g;
           b = _b;
           totalLuminosity = r + g + b;
       }
       
       @Override
       public int compareTo(Pixel o) {
           return this.totalLuminosity == o.totalLuminosity ? 0 :
                   (this.totalLuminosity > o.totalLuminosity ? 1 : -1);
       }
       
       @Override
       public boolean equals(Object c) {
           if (c instanceof Pixel) {
        	   Pixel p = (Pixel) c;
               return this.r == p.r && this.g == p.g && this.b == p.b;
           }
           
           return false;
       }

       @Override
       public int hashCode() {
           int hash = 5;
           hash = 19 * hash + this.r;
           hash = 19 * hash + this.g;
           hash = 19 * hash + this.b;
           return hash;
       }
   }

