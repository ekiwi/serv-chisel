// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

class BufReg extends Module {
  val io = IO(new BufRegIO)
  val enabled = !io.state.hold

  val clearLsb = io.count.count0 && io.decode.clearLsb

  val carry = Reg(UInt(1.W))
  val c_q = (io.rs1 & io.decode.rs1En) +& (io.imm & io.decode.immEn & !clearLsb) + carry
  val q = c_q(0)
  // clear carry when not in init state
  carry := c_q(1) & io.count.init

  val data = Reg(UInt(32.W))
  val newData = Mux(io.decode.loop && !io.count.init, data(0), q)
  when(enabled) { data := newData ## data(31, 1) }
  io.dataOut := data(0)
  io.dataBusAddress := data(31,2) ## "b00".U(2.W)

  val (lsb_1, lsb_0) = (Reg(UInt(1.W)), Reg(UInt(1.W)))
  io.lsb := lsb_1 ## lsb_0
  when(io.count.count0) {
    lsb_0 := q
  }
  when(io.count.count1) {
    lsb_1 := q
  }
}

class BufRegIO extends Bundle {
  val count = Input(new CountIO)
  val decode = Flipped(new DecodeToBufRegIO)
  val state = Flipped(new StateToBufRegIO)
  val imm = Input(UInt(1.W))
  val rs1 = Input(UInt(1.W))
  val lsb = Output(UInt(2.W))
  val dataBusAddress = Output(UInt(32.W))
  val dataOut = Output(UInt(1.W))
}