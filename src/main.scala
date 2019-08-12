// Copyright 2019 The Regents of the University of California
// Copyright 2019 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

import chisel3._

object main extends App {

  val m: Map[String, String] = Map(
    "en"        -> "i_en",
    "rs1"       -> "i_rs1",
    "op_b"      -> "i_op_b",
    "buf"       -> "i_buf",
    "init"      -> "i_init",
    "cnt_done"  -> "i_cnt_done",
    "sub"       -> "i_sub",
    "bool_op"   -> "i_bool_op",
    "cmp.sel"   -> "i_cmp_sel",
    "cmp.neg"   -> "i_cmp_neg",
    "cmp.uns"   -> "i_cmp_uns",
    "cmp.out"   -> "o_cmp",
    "shamt_en"  -> "i_shamt_en",
    "sh.right"  -> "i_sh_right",
    "sh.signed" -> "i_sh_signed",
    "sh.done"   -> "o_sh_done",
    "rd.sel"    -> "i_rd_sel",
    "rd.out"    -> "o_rd"
  )

  Driver.execute(args, Adapter(() => new serv_alu(), "clk", "i_rst", m))

  // make add
  Driver.execute(args, Adapter(() => new ser_add(), "clk", "rst"))

  Driver.execute(args, Adapter(() => new ser_lt(), "i_clk", "TODO_remove", Map(
    "a" -> "i_a", "b" -> "i_b", "clr" -> "i_clr", "sign" -> "i_sign", "q" -> "o_q"
  )))

  Driver.execute(args, Adapter(() => new ser_shift(), "i_clk", "i_rst", Map(
    "load" -> "i_load", "shamt" -> "i_shamt", "shamt_msb" -> "i_shamt_msb",
    "signbit" -> "i_signbit", "right" -> "i_right", "done" -> "o_done",
    "d" -> "i_d", "q" -> "o_q"
  )))
}
