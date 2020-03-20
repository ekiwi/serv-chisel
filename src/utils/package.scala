// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>


package object utils {
  implicit class UIntSlice[U <: chisel3.UInt](x: U) {
    def slice(left: Int, right: Int): chisel3.UInt = {
      val width = x.getWidth
      val hi = if(left >= 0) left else width + left - 1
      val lo = if(right >= 0) right else width + right
      assert(hi >= lo, s"$hi < $lo!")
      x(hi, lo)
    }

    def split(n: Int): Split = {
      val width = x.getWidth
      Split(x.head(width-n), x.tail(width-n))
    }
  }
  case class Split(msb: chisel3.UInt, lsb: chisel3.UInt)
}
