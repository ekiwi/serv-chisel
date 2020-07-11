// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

class DecodeToStateIO extends Bundle {
  val takeBranch = Output(Bool())
  val eOp = Output(Bool())
  val eBreak = Output(Bool())
  val branchOp = Output(Bool())
  val memOp = Output(Bool())
  val shiftOp = Output(Bool())
  val sltOp = Output(Bool())
  val rdOp = Output(Bool())
}

class DecodeToControlIO extends Bundle {
  val jalOrJalr = Output(Bool())
  val uType = Output(Bool())
  val pcRel = Output(Bool())
  val mRet = Output(Bool())
}

class Decode {

}
