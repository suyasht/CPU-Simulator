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
//            if(globals.isbranch_fetch && !(globals.check_stall)&&!(ins.getOpcode().toString().equals("BRA")))
//            {
//                globals.isbranch_fetch=false;
//                return;
//            }
            if (ins.isNull()) return;            
            
//           if(ins.getOpcode().toString().equals("BRA"))
//           {
//                globals.isbranch_fetch=true;
//                
//           }
            if(globals.branch_flag)
               {
                   return;               
               }
            
            //pc++;
            // Do something idempotent to compute the next program counter.
            
            // Don't forget branches, which MUST be resolved in the Decode
            // stage.  You will make use of global resources to commmunicate
            // between stages.
            
            // Your code goes here...
            
            output.setInstruction(ins);
       
               
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
            GlobalData globals = (GlobalData)core.getGlobalResources();
            if(!globals.check_stall && !output_reg.isMasterBubble())
            {                
                globals.program_counter++;
            }
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase<FetchToDecode,DecodeToExecute> {
        
        public Decode(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this to deal with 
            // dependencies.
            GlobalData globals = (GlobalData)core.getGlobalResources();
            return globals.check_stall;
        }
        

        @Override
        public void compute(FetchToDecode input, DecodeToExecute output) {
            InstructionBase ins = input.getInstruction();            
            // These null instruction checks are mostly just to speed up
            // the simulation.  The Void types were created so that null
            // checks can be almost completely avoided.
            
            if (ins.isNull()) return;            
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            int pc=globals.program_counter;
           
            for(int x=0;x<=31;x++)
            {
                System.out.print(" "+ globals.register_invalid[x]);               
                
            }
            System.out.println("\n");
            //if((ins.getSrc1().isRegister() && ins.getSrc2().isRegister()) == true || (ins.getSrc1().isRegister() || ins.getSrc2().isRegister())== true)
            
            // ____________________STALL IMPLEMETATION_____________________________________________
            
            int index_execute=0;
            int index_memory=0;
            int index_writeback=0;
            
            if(ins.getOpcode().toString().equals("BRA"))
            {
                globals.branch_flag=true;
            }
              if(ins.getOper0().isRegister())
                {
                    if(globals.register_invalid[ins.getOper0().getRegisterNumber()] == true)
                    {    
                        globals.check_stall= true;          
                        index_execute=core.getForwardingDestinationRegisterNumber(1);
                        index_memory=core.getForwardingDestinationRegisterNumber(2);
                        index_writeback=core.getForwardingDestinationRegisterNumber(3);

                        if(ins.getSrc1().getRegisterNumber() == index_execute)
                        {
                            globals.result_execute= 1;
                        }
                        if(ins.getSrc1().getRegisterNumber() == index_memory)
                        {
                            globals.result_memory=1;
                        }
                        if(ins.getSrc1().getRegisterNumber() == index_writeback)
                        {
                            globals.result_writeback=1;
                        }
                        globals.forwardingflag=true;
                        return;                        
                        
                    }
                    else
                        globals.check_stall=false;
                }
                if( ins.getSrc1().isRegister())
                {                    
                            if(globals.register_invalid[ins.getSrc1().getRegisterNumber()] == true)
                            {                                                   
                                globals.check_stall_src1= true;
                                index_execute=core.getForwardingDestinationRegisterNumber(1);
                                index_memory=core.getForwardingDestinationRegisterNumber(2);
                                index_writeback=core.getForwardingDestinationRegisterNumber(3);
                                
                                if(ins.getSrc1().getRegisterNumber() == index_execute)
                                {
                                    globals.result_execute= 1;
                                }
                                if(ins.getSrc1().getRegisterNumber() == index_memory)
                                {
                                    globals.result_memory=1;
                                }
                                if(ins.getSrc1().getRegisterNumber() == index_writeback)
                                {
                                    globals.result_writeback=1;
                                }
                                globals.forwardingflag=true;
                                return;
                            }
                            else
                                globals.check_stall= false;                        
                }
                if((globals.check_stall== false) && ins.getSrc2().isRegister())
                {
                            if(globals.register_invalid[ins.getSrc2().getRegisterNumber()] == true)
                            {                    
                                globals.check_stall_src2=true;                                                                
                                index_execute=core.getForwardingDestinationRegisterNumber(0);                                
                                index_memory=core.getForwardingDestinationRegisterNumber(1);
                                index_writeback=core.getForwardingDestinationRegisterNumber(2);
                                
                                if(ins.getSrc1().getRegisterNumber() == index_execute)
                                {
                                    globals.result_execute=1;
                                }
                                if(ins.getSrc1().getRegisterNumber() == index_memory)
                                {
                                    globals.result_memory=1;
                                }
                                if(ins.getSrc1().getRegisterNumber() == index_writeback)
                                {
                                    globals.result_writeback=1;
                                }
                                globals.forwardingflag=true;
                                return;
                            }
                            else
                                globals.check_stall=false;

                }  
                    if(!globals.check_stall)
                    {
                        if(!((ins.getOpcode().toString().equals("HALT")) && (ins.getOpcode().toString().equals("JMP"))))
                        {                            
                            if(ins.getOper0().isRegister())
                            {
                                ins.getOper0().setValue(globals.register_file[ins.getOper0().getRegisterNumber()]);
                                globals.register_invalid[ins.getOper0().getRegisterNumber()]=true;   
                            }                                                    
                            if(ins.getSrc1().isRegister())
                            {
                                ins.getSrc1().setValue(globals.register_file[ins.getSrc1().getRegisterNumber()]);
                                globals.register_invalid[ins.getOper0().getRegisterNumber()]=true;   
                            }
//                            else
//                            {
//                                ins.getSrc1().setValue(ins.getSrc1().getValue());
//                            }
                            if(ins.getSrc2().isRegister())
                            {
                                ins.getSrc2().setValue(globals.register_file[ins.getSrc2().getRegisterNumber()]);
                                globals.register_invalid[ins.getOper0().getRegisterNumber()]=true;   
                            }
//                            else
//                            {
//                                ins.getSrc2().setValue(ins.getSrc2().getValue());
//                            }
                        }
                    } 
                    else
                    {
                        input_reg.setSlaveStall(true);
                    }
                        if(ins.getOpcode().toString().equals("JMP"))
                        {
                            globals.program_counter=ins.getLabelTarget().getAddress();
                        }
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        if(ins.getOpcode().toString()!="JMP")
                        output.setInstruction(ins);
                        // Set other data that's passed to the next stage.                              
                     }
                
              /*  else 
                {
                    globals.program_counter=ins.getLabelTarget().getAddress();                          
                    globals.flag=1;
                }
            }
            else
            {
                globals.flag=0;
            }
*/           
            
            // Do what the decode stage does:
            // - Look up source operands
            // - Decode instruction
            // - Resolve branches            

            
        
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
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int pc=globals.program_counter;
            int result_execute=0;
            if(ins.getOpcode().toString().equals("BRA"))
            {                    
                if(ins.getComparison().toString().equals("LT"))
                {            
                    if(!globals.check_stall)
                    {
                        if(ins.getOper0().isRegister())
                        {
                                System.out.println("source 1:- "+ins.getOper0().getValue());
                            if(globals.register_file[ins.getOper0().getRegisterNumber()] < 0)
                            {
                                globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                                globals.program_counter=ins.getLabelTarget().getAddress()-1;
                                globals.branch_flag=false;
                            }
                            else
                            {
                                 globals.program_counter=pc-2;
                                 globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                                 globals.branch_flag=false;
                            }
                        }
                    }
                }
                else if(ins.getComparison().toString().equals("EQ"))
                {                                                   
                    if(!globals.check_stall)
                    {
                        if(globals.register_file[ins.getOper0().getRegisterNumber()] == 0)
                        {
                            globals.program_counter=ins.getLabelTarget().getAddress()-1;
                            globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                            globals.branch_flag=false;                        
                        }
                        else
                        {
                            globals.program_counter=pc-2;
                            globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                            globals.branch_flag=false;
                            
                        }
                    }
                }
                else if(ins.getComparison().toString().equals("GE"))
                {                                              
                    if(!globals.check_stall)
                    {
                                            
                        if(globals.register_file[ins.getOper0().getRegisterNumber()]>=0 || globals.register_file[ins.getOper0().getRegisterNumber()]==0)
                        {
                            globals.program_counter=ins.getLabelTarget().getAddress()-1;       
                            globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                            globals.branch_flag=false;
                        }
                        else
                        {
                            globals.program_counter=pc-2;
                            globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
                            globals.branch_flag=false;  
                        }
                    }
                }
            }
            else{
            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().getValue();
             int result=0;
            if(globals.forwardingflag)
            {
                if(globals.result_execute==1)
                {
                    globals.resultin_execute=result;
                    source1=globals.result_execute;                    
                }
                if(globals.result_memory==1)
                {
                    globals.resultin_execute=globals.resultin_memory;
                    source1=globals.result_memory;
                }
                if(globals.result_writeback==1)
                {
                    globals.resultin_execute=globals.resultin_writeback;
                    source1=globals.result_writeback;
                }
                
            }
            if(globals.forwardingflag)
            {
                if(globals.check_stall_src1)
                {
                    if(globals.result_execute==1)
                    {
                        globals.resultin_execute=result;
                        source1=globals.result_execute;                    
                    }
                    if(globals.result_memory==1)
                    {
                        globals.resultin_execute=globals.resultin_memory;
                        source1=globals.result_memory;
                    }
                    if(globals.result_writeback==1)
                    {
                        globals.resultin_execute=globals.resultin_writeback;
                        source1=globals.result_writeback;
                    }
                }
                if(globals.check_stall_src2)
                {
                    if(globals.result_execute==1)
                    {
                        globals.resultin_execute=result;
                        source2=globals.result_execute;                    
                    }
                    if(globals.result_memory==1)
                    {
                        globals.resultin_execute=globals.resultin_memory;
                        source2=globals.result_memory;
                    }
                    if(globals.result_writeback==1)
                    {
                        globals.resultin_execute=globals.resultin_writeback;
                        source2=globals.result_writeback;
                    }
                }
                
            }
            
            else
            {
            
                result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
            
            output.re=result;
             
            }
                        
            // Fill output with what passes to Memory stage...
            output.setInstruction(ins);
            // Set other data that's passed to the next stage.
            }
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
                if(ins.getSrc1().isRegister())
                {   
                    System.out.print((ins.getSrc1().getValue()));
                    System.out.print(globals.memory_space[(ins.getSrc1().getValue())]);
                    globals.register_file[ins.getOper0().getRegisterNumber()]=globals.memory_space[(ins.getSrc1().getValue())];                                
                }                    

                globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;                                        
            }
            if(ins.getOpcode().toString().equals("STORE"))
            {
                {
                    globals.memory_space[input.re]=ins.getOper0().getValue();
                }
                globals.register_invalid[ins.getOper0().getRegisterNumber()]=false;
            }
            output.res=input.re;  
            globals.resultin_memory=input.re;
            // Access memory...  
            if(ins.getOpcode().toString()!="LOAD")
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
            int res= input.res;
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
              globals.register_file[ins.getOper0().getRegisterNumber()]=input.res;
            // Write back result to register file
//            if(!((ins.getOpcode().toString().equals("HALT"))|| (ins.getOpcode().toString().equals("JMP"))||(ins.getOpcode().toString().equals("BRA"))
//                 || (ins.getOpcode().toString().equals("LOAD"))))
//            {
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
            //}                       
            globals.result_writeback=input.res;
            for(int x=0;x<=31;x++)
            {
                System.out.print(" "+ globals.register_file[x]);
            }
            System.out.println("\n");
            for(int x=0;x<=99;x++)
            {
                System.out.print(" "+ globals.memory_space[x]);
            }
            System.out.println("\n");
            if (input.getInstruction().getOpcode() == EnumOpcode.OUT)
            {
                System.out.println(globals.memory_space[ins.getOper0().getValue()]);
            }
            
            if (input.getInstruction().getOpcode() == EnumOpcode.HALT) {
                System.exit(0);
                // Stop the simulation
            }
        }
    }
}