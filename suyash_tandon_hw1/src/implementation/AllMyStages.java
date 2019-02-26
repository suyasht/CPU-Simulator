/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import implementation.AllMyLatches.*;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;
import utilitytypes.Operand;

/**
 * The AllMyStages class merely collects together all of the pipeline stage 
 * classes into one place.  You are free to split them out into top-level
 * classes.
 * 
 * Each inner class here implements the logic for a pipeline stage.
 * 
 * It is recommended that the compute methods be idempotent.  This means
 * that if compute is called multiple times in a clock cycle, it should
 * compute the same output for the same input.
 * 
 * How might we make updating the program counter idempotent?
 * 
 * @author
 */
public class AllMyStages {
    public static String s;
    /*** Fetch Stage ***/
    static class Fetch extends PipelineStageBase<VoidLatch,FetchToDecode> {
        public Fetch(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        public String getStatus() {
            // Generate a string that helps you debug.
            return null;
        }

        @Override
        public void compute(VoidLatch input, FetchToDecode output) {
            
            GlobalData globals = (GlobalData)core.getGlobalResources();
            System.out.println("Cycle number is:" +globals.temp);
            System.out.println("\n");
            globals.temp++;
            
            int pc = globals.program_counter;
            // Fetch the instruction
            System.out.println("Program Counter Value: "+pc);
            InstructionBase ins = globals.program.getInstructionAt(pc);
            if (ins.isNull()) return;
            System.out.println("Instruction at fetch atage "+ ins);
            
            if(s=="BRA")
            {
                
            }
            
            //pc++;
            // Do something idempotent to compute the next program counter.
            
            // Don't forget branches, which MUST be resolved in the Decode
            // stage.  You will make use of global resources to commmunicate
            // between stages.
            
            // Your code goes here...
            
            output.setInstruction(ins);
               globals.program_counter++;
        }
        
        @Override
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this for when branches
            // are being resolved.
            return false;
        }
        
        
        /**
         * This function is to advance state to the next clock cycle and
         * can be applied to any data that must be updated but which is
         * not stored in a pipeline register.
         */
        @Override
        public void advanceClock() {
            // Hint:  You will need to implement this help with waiting
            // for branch resolution and updating the program counter.
            // Don't forget to check for stall conditions, such as when
            // nextStageCanAcceptWork() returns false.
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase<FetchToDecode,DecodeToExecute> {
        boolean check_stall=false;
        public Decode(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this to deal with 
            // dependencies.
            return check_stall;
        }
        

        @Override
        public void compute(FetchToDecode input, DecodeToExecute output) {
            InstructionBase ins = input.getInstruction();
            System.out.println("Instruction at decode stage: "+ins);
            // These null instruction checks are mostly just to speed up
            // the simulation.  The Void types were created so that null
            // checks can be almost completely avoided.
            
            if (ins.isNull()) return;            
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            s=ins.getOpcode().toString();
           
            
            //if((ins.getSrc1().isRegister() && ins.getSrc2().isRegister()) == true || (ins.getSrc1().isRegister() || ins.getSrc2().isRegister())== true)
            
            // ____________________STALL IMPLEMETATION_____________________________________________
            
            if(globals.flag==0)  
            {
                if(ins.getOper0().isRegister())
                {
                    if(globals.register_invalid[ins.getOper0().getRegisterNumber()] == true)
                    {    
                        check_stall= true;
                                
                    }
                    else
                        check_stall=false;
                }
                if( (check_stall==false) && ins.getSrc1().isRegister())
                {
                    //System.out.println("Registername" +ins.getSrc2().getRegisterNumber());
                            if(globals.register_invalid[ins.getSrc1().getRegisterNumber()] == true)
                            {                                                   
                                check_stall= true;
                            }
                            else
                                check_stall= false;                        
                }
                if((check_stall== false) && ins.getSrc2().isRegister())
                {
                            if(globals.register_invalid[ins.getSrc2().getRegisterNumber()] == true)
                            {                    
                                check_stall=true;
                            }
                            else
                                check_stall=false;

                }  
            
                if(check_stall==true)
                {                   
                    globals.program_counter--;
                }
                
                else if(check_stall==false)
                {
                    if(!(ins.getOpcode().toString().equals("HALT")))
                    {
                        if(!(ins.getOpcode().toString().equals("JMP")))
                        {
                            if(!(ins.getOpcode().toString().equals("BRA")))
                            {
                             
                                ins.getOper0().setValue(ins.getOper0().getRegisterNumber());
                                globals.register_invalid[ins.getOper0().getRegisterNumber()]=true;                    
                                if(ins.getSrc1().isRegister())
                                {

                                    ins.getSrc1().setValue(globals.register_file[ins.getSrc1().getRegisterNumber()]);
                                    globals.register_invalid[ins.getSrc1().getRegisterNumber()]=true;
                                }
                                else
                                {
                                    ins.getSrc1().setValue(ins.getSrc1().getValue());
                                }
                                if(ins.getSrc2().isRegister())
                                {
                                    ins.getSrc2().setValue(globals.register_file[ins.getSrc2().getRegisterNumber()]);
                                    globals.register_invalid[ins.getSrc2().getRegisterNumber()]=true;
                                }
                                else
                                {
                                    ins.getSrc2().setValue(ins.getSrc2().getValue());
                                }
                            }

                        }
                    }
                    //________BRANCHING IMPLEMENTATION____________________
                              
                        if(ins.getOpcode().toString().equals("BRA"))
                        {

                            System.out.println("Value after branch "+globals.register_file[ins.getOper0().getRegisterNumber()]);
                            if(ins.getComparison().toString().equals("LT"))
                            {                                                    
                                if(globals.register_file[ins.getOper0().getRegisterNumber()]<0)
                                {
                                    globals.program_counter=ins.getLabelTarget().getAddress();                          
                                    globals.flag=1;
                                }
                            }
                            else if(ins.getComparison().toString().equals("EQ"))
                            {                                                   
                                if(globals.register_file[ins.getOper0().getRegisterNumber()] == 0)
                                {
                                    globals.program_counter=ins.getLabelTarget().getAddress();
                                    globals.flag=1;
                                }
                            }
                            else if(ins.getComparison().toString().equals("GE"))
                            {                                              
                                if(globals.register_file[ins.getOper0().getRegisterNumber()]>0)
                                {
                                    globals.program_counter=ins.getLabelTarget().getAddress();
                                    globals.flag=1;
                                }
                            }
                        }
                        output.setInstruction(ins);
                        // Set other data that's passed to the next stage.                              
                }
                
                else 
                {
                    globals.program_counter=ins.getLabelTarget().getAddress();                          
                    globals.flag=1;
                }
            }
            else
            {
                globals.flag=0;
            }
           
            
            // Do what the decode stage does:
            // - Look up source operands
            // - Decode instruction
            // - Resolve branches            

            
        }
    }
    

    /*** Execute Stage ***/
    static class Execute extends PipelineStageBase<DecodeToExecute,ExecuteToMemory> {
        public Execute(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(DecodeToExecute input, ExecuteToMemory output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;

            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().getValue();

            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
            
            output.re=result;
                
            System.out.println("Instruction at Execute stage"+ ins);
                        
            // Fill output with what passes to Memory stage...
            output.setInstruction(ins);
            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Memory Stage ***/
    static class Memory extends PipelineStageBase<ExecuteToMemory,MemoryToWriteback> {
        public Memory(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(ExecuteToMemory input, MemoryToWriteback output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;
            String opcode1=ins.getOpcode().toString();
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] mem=globals.memory_space;
            int[] regfile = globals.register_file;
            if(ins.getOpcode().toString().equals("LOAD"))
                {
                    if(ins.getSrc1().isRegister() && ins.getSrc2().isRegister())
                    {    
                        globals.register_file[ins.getOper0().getRegisterNumber()]=globals.memory_space[(ins.getSrc1().getRegisterNumber())+(ins.getSrc2().getRegisterNumber())];
                    }
                    else if(ins.getSrc1().isRegister() && !(ins.getSrc2().isRegister()))
                    {
                        globals.register_file[ins.getOper0().getRegisterNumber()]=globals.memory_space[ins.getSrc1().getRegisterNumber()+(ins.getSrc2().getValue())];
                    }
                    else if((ins.getSrc2().isRegister() && !(ins.getSrc1().isRegister())))
                    {
                        globals.register_file[ins.getOper0().getRegisterNumber()]=globals.memory_space[ins.getSrc1().getValue()+(ins.getSrc2().getRegisterNumber())];
                    }
                    if(ins.getOper0().isRegister())
                    {
                        globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                    }
                    if(ins.getSrc1().isRegister())
                    {   
                        globals.register_invalid[ins.getSrc1().getRegisterNumber()]=false;
                    }
                    if(ins.getSrc2().isRegister())
                    {
                        globals.register_invalid[ins.getSrc2().getRegisterNumber()]=false;
                    }
                }
            else if(ins.getOpcode().toString().equals("STORE"))
                {
                    
                    if(ins.getSrc1().isRegister() && ins.getSrc2().isRegister())
                    {    
                        globals.memory_space[ins.getOper0().getRegisterNumber()]=globals.register_file[(ins.getSrc1().getRegisterNumber())+(ins.getSrc2().getRegisterNumber())];
                    }
                    else if(ins.getSrc1().isRegister() && !(ins.getSrc2().isRegister()))
                    {
                        globals.memory_space[ins.getOper0().getRegisterNumber()]=globals.register_file[ins.getSrc1().getRegisterNumber()+(ins.getSrc2().getValue())];
                    }
                    else if((ins.getSrc2().isRegister() && !(ins.getSrc1().isRegister())))
                    {
                        globals.memory_space[ins.getOper0().getRegisterNumber()]=globals.register_file[ins.getSrc1().getValue()+(ins.getSrc2().getRegisterNumber())];
                    }
                    if(ins.getOper0().isRegister())
                    {
                        globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                    }
                    if(ins.getSrc1().isRegister())
                    {   
                        globals.register_invalid[ins.getSrc1().getRegisterNumber()]=false;
                    }
                    if(ins.getSrc2().isRegister())
                    {
                        globals.register_invalid[ins.getSrc2().getRegisterNumber()]=false;
                    }
                }
            output.re=input.re;    
            // Access memory...
            System.out.println(ins+" " +output.re+ " Stored in R"+ ins.getOper0().getRegisterNumber());
            System.out.println("Instruction at Memory stage "+ ins);
            output.setInstruction(ins);
            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase<MemoryToWriteback,VoidLatch> {
        public Writeback(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(MemoryToWriteback input, VoidLatch output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;
            int res= input.re;
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            globals.register_file[ins.getOper0().getRegisterNumber()]=input.re;
            // Write back result to register file
            if(!((ins.getOpcode().toString().equals("HALT"))|| (ins.getOpcode().toString().equals("JMP"))||(ins.getOpcode().toString().equals("BRA"))
                 ||(ins.getOpcode().toString().equals("STORE")) || (ins.getOpcode().toString().equals("LOAD"))))
 
            {
                if(ins.getOper0().isRegister())
                {
                   globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                }
                if(ins.getSrc1().isRegister())
                {   
                   globals.register_invalid[ins.getSrc1().getRegisterNumber()]=false;
                }
                if(ins.getSrc2().isRegister())
                {
                   globals.register_invalid[ins.getSrc2().getRegisterNumber()]=false;
                }
            }
   
            
            System.out.println("Instruction at Writeback stage "+ ins);
            
            if (input.getInstruction().getOpcode() == EnumOpcode.HALT) {
                System.exit(0);
                // Stop the simulation
            }
        }
    }
}