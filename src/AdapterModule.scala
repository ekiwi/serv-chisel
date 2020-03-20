// Copyright 2019 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

import chisel3._
import chisel3.experimental.{DataMirror, Direction}

object Adapter {
  def apply(m: () => Module, clock: String, reset: String, io: Map[String, String] = Map()): () => RawModule = {
    val rename = (n: String) => {
      if(n.startsWith("io.")) {
        val needle = n.substring(3)
        io.get(needle) match {
          case Some(name) => name
          case None => needle.replace(".", "_")
        }
      } else {
        n match {
          case "clock" => clock
          case "reset" => reset
          case other => throw new RuntimeException(s"unexpected id: $other")
        }
      }
    }
    () => { new AdapterModule(m, rename) }
  }
}

/** wraps a chisel module in order to change the name of its ports */
class AdapterModule(makeModule: () => Module, rename: String => String, invertReset: Boolean = false) extends RawModule {
  val clock = IO(Input(Clock()))
  clock.suggestName(rename("clock"))
  val reset = IO(Input(Bool()))
  reset.suggestName(rename("reset"))
  val m_reset = if(invertReset) { !reset } else { reset }

  val m = withClockAndReset(clock, reset) { Module(makeModule()) }

  override val desiredName : String = s"${m.name}_wrapper"

  private def declareAndConnect(data: Element) = {
    val isInput = DataMirror.directionOf(data) == Direction.Input
    assert(isInput || DataMirror.directionOf(data) == Direction.Output, s"${DataMirror.directionOf(data)}")
    val name = data.toNamed.name
    val new_name = rename(name)
    assert(!new_name.contains("."), s"Only scalar IO supported ($name => $new_name)")
    val pin = if(isInput){ IO(Input(data.cloneType)) } else { IO(Output(data.cloneType)) }
    if(isInput) { data := pin } else { pin := data }
    pin.suggestName(new_name)
  }

  private def declareAndConnect(r: Record): Unit = {
    r.elements.foreach{ case (name, data) =>
        data match {
          case d: Element => declareAndConnect(d)
          case r: Record => declareAndConnect(r)
          case other =>
            throw new NotImplementedError(s"Unsupported element type: $other")
        }
    }
  }

  declareAndConnect(m.io)
}
