// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package formal

import org.scalatest._
import paso._
import chisel3._
import chisel3.util._
import serv.ServTopWithRam

class ServProtocols(impl: serv.ServTopWithRam) extends ProtocolSpec[RiscVSpec] {
  val spec = new RiscVSpec

  override val stickyInputs = true

  // this protocol should work for any reg2reg instruction
  protocol(spec.add)(impl.io) { (clock, dut, in) =>
    dut.timerInterrupt.poke(false.B) // no interrupts
    dut.dbus.ack.poke(false.B) // no data bus transactions
    // apply instruction
    dut.ibus.rdt.poke(in.toInstruction(funct7 = 0.U, funct3 = 0.U, opcode = "b0110011".U))
    dut.ibus.ack.poke(true.B)
    dut.ibus.cyc.expect(true.B) // transaction should be immediately acknowledged
    clock.step()

    // ibus should be idle while we are executing the instruction
    dut.ibus.rdt.poke(DontCare)
    dut.ibus.ack.poke(false.B)

    // cyc will become true once the instruction has executed
    do_while(!dut.ibus.cyc.peek(), 64) {
      clock.step()
    }

    // one cycle with cyc high
    clock.step()
  }
}

class ServProof(impl: serv.ServTopWithRam, spec: RiscVSpec) extends ProofCollateral(impl, spec) {
  // map data in the register file to the actual RAM
  mapping { (impl, spec) =>
    forall(1 until 32) { ii =>
      // read and combine 2bit values from ram
      val parts = (0 until 16).reverse.map(jj => impl.ram.memory.read(ii * 16.U + jj.U))
      assert(spec.reg.read(ii) === Cat(parts))
    }
  }

  invariants { impl =>

  }
}


class ServSpec extends FlatSpec {
  behavior of "serv.ServTopWithRam"

  it should "correctly implement the instructions" in {
    Paso(new ServTopWithRam(true))(new ServProtocols(_)).proof(Paso.MCZ3, new ServProof(_, _))
  }
}
