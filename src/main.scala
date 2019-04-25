// Copyright 2019 The Regents of the University of California
// Copyright 2019 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

import chisel3._

object main extends App {
  Driver.execute(args, () => new serv_alu())
}
