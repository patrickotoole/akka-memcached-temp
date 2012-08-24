package com.klout.akkamemcache

import akka.actor.IO
import akka.util.ByteString
import com.klout.akkamemcache.{GetResult, Found}


object Iteratees {
    import Constants._
    def ascii(bytes: ByteString): String = bytes.decodeString("US-ASCII").trim

    val ioActor = Tester.ioActor
    val whiteSpace = Set(' ','\r').map(_.toByte)
    
    def continue (byte: Byte): Boolean = {
        !whiteSpace.contains(byte)
    }

    val readLine: IO.Iteratee[Option[GetResult]] = {
        (IO takeWhile continue) flatMap {
            case Value =>
                processValue
            case Deleted => {
                println("DELETED")
                IO takeUntil CRLF map { _ =>
                    println("A key was deleted")
                    None
                }
            }
            case Stored => {
                IO takeUntil CRLF map { _ =>
                    println("A key was stored")
                    None
                }
            }
            case Error => {
                IO takeUntil CRLF map { _ =>
                    println("An error occurred")
                    None
                }
            }
            case End => {
                println("END!")
                IO takeUntil CRLF map { _ =>
                    println("Key not found")
                    Some(com.klout.akkamemcache.NotFound("key"))
                }
            }

            case NotFound => {
                IO takeUntil CRLF map { _ => 
                    println("Delete failed: key not found")
                    Some(com.klout.akkamemcache.NotFound("key"))
                }
            }

            case other => {
                IO takeUntil CRLF map { 
                    data =>
                        println("Unexpected output from Memcached: "+ ascii(other) + ". " + ascii(data))
                        None
                }
            }
        }
    }

    val processValue: IO.Iteratee[Option[GetResult]] =
        for {
            _ <- IO take 1
            key <- IO takeUntil Space
            id  <- IO takeUntil Space
            length <- IO takeUntil CRLF map (ascii(_).toInt)
            value <- IO take length
            newline <- IO takeUntil CRLF
            end <- IO takeUntil CRLF
        } yield {
            // println ("key: [%s], length: [%d], value: [%s]".format(key, length, value))
            Some(Found(ascii(key),value))
        }

    val processLine: IO.Iteratee[Unit] = {
        IO repeat{
            readLine map {
                case Some(getResult) => {
                    ioActor ! getResult
                }
                case _ => {}
            }
        }
    }


}

object Constants {

    val Space = ByteString(" ")

    val CRLF = ByteString("\r\n")

    val Value = ByteString("VALUE")

    val Deleted = ByteString("DELETED")

    val Stored = ByteString("STORED")

    val Error = ByteString("ERROR")

    val NotFound = ByteString("NOT_FOUND")

    val End = ByteString("END")



}

object Protocol {
    import Constants._

    trait Command {
        def toByteString: ByteString
    }
    case class SetCommand(key: String, payload: ByteString, ttl: Long) extends Command {
        override def toByteString = ByteString("set " + key + " " + ttl + " 0 " + payload.size) ++ CRLF ++ payload ++ CRLF
    }

    case class DeleteCommand(key: String) extends Command {
        override def toByteString = ByteString("delete "+key ) ++ CRLF
    }

    case class GetCommand(key: String) extends Command {
        override def toByteString = ByteString("get " + key) ++ CRLF
    }

}
