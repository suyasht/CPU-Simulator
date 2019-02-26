/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import tools.MyALU;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;
import baseclasses.Latch;
import static baseclasses.PipelineStageBase.operNames;
import cpusimulator.CpuSimulator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import static utilitytypes.EnumOpcode.*;
import utilitytypes.ICpuCore;
import utilitytypes.IGlobals;
import utilitytypes.IPipeReg;
import static utilitytypes.IProperties.*;
import utilitytypes.IRegFile;
import utilitytypes.Logger;
import utilitytypes.Operand;
import voidtypes.VoidLabelTarget;

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
    /*** Fetch Stage ***/
    static class Fetch extends PipelineStageBase {
        public Fetch(ICpuCore core) {
            super(core, "Fetch");
        }
        
        // Does this state have an instruction it wants to send to the next
        // stage?  Note that this is computed only for display and debugging
        // purposes.
        boolean has_work;
                
        /** 
         * For Fetch, this method only has diagnostic value.  However, 
         * stageHasWorkToDo is very important for other stages.
         * 
         * @return Status of Fetch, indicating that it has fetched an 
         *         instruction that needs to be sent to Decode.
         */
        @Override
        public boolean stageHasWorkToDo() {
            return has_work;
        }
        
        @Override
        public String getStatus() {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            if (globals.getPropertyInteger("branch_state_fetch") == GlobalData.BRANCH_STATE_WAITING) {
                addStatusWord("ResolveWait");
            }
            return super.getStatus();
        }

        @Override
        public void compute(Latch input, Latch output) {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            
            // Get the PC and fetch the instruction
            int pc_no_branch    = globals.getPropertyInteger(PROGRAM_COUNTER);
            int pc_taken_branch = globals.getPropertyInteger("program_counter_takenbranch");
            int branch_state_decode = globals.getPropertyInteger("branch_state_decode");
            int branch_state_fetch = globals.getPropertyInteger("branch_state_fetch");
            int pc = (branch_state_decode == GlobalData.BRANCH_STATE_TAKEN) ?
                    pc_taken_branch : pc_no_branch;
            InstructionBase ins = globals.getInstructionAt(pc);
            
            // Initialize this status flag to assume a stall or bubble condition
            // by default.
            has_work = false;
            
            // If the instruction is NULL (like we ran off the end of the
            // program), just return.  However, for diagnostic purposes,
            // we make sure something meaningful appears when 
            // CpuSimulator.printStagesEveryCycle is set to true.
            if (ins.isNull()) {
                // Fetch is working on no instruction at no address
                setActivity("");
            } else {            
                // Since there is no input pipeline register, we have to inform
                // the diagnostic helper code explicitly what instruction Fetch
                // is working on.
                has_work = true;
                output.setInstruction(ins);
                setActivity(ins.toString());
            }
            
            // If the output cannot accept work, then 
            if (!output.canAcceptWork()) return;
            
//            Logger.out.println("No stall");
            globals.setClockedProperty(PROGRAM_COUNTER, pc + 1);
            
            boolean branch_wait = false;
            if (branch_state_fetch == GlobalData.BRANCH_STATE_WAITING) {
                branch_wait = true;
            }
            if (branch_state_decode != GlobalData.BRANCH_STATE_NULL) {
//                Logger.out.println("branch state resolved");
                globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_NULL);
                globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NULL);
                branch_wait = false;
            }
            if (!branch_wait) {
                if (ins.getOpcode().isBranch()) {
                    globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_WAITING);
                    System.out.println("Instruction at fetch:" +ins);
                }
            }
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase {
        public Decode(ICpuCore core) {
            super(core, "Decode");
        }
        
        
        // When a branch is taken, we have to squash the next instruction
        // sent in by Fetch, because it is the fall-through that we don't
        // want to execute.  This flag is set only for status reporting purposes.
        boolean squashing_instruction = false;
        boolean shutting_down = false;

        @Override
        public String getStatus() {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            String s = super.getStatus();
            if (globals.getPropertyBoolean("decode_squash")) {
                s = "Squashing";
            }
            return s;
        }
        

//        private static final String[] fwd_regs = {"ExecuteToWriteback", 
//            "MemoryToWriteback"};
        
        @Override
        public void compute(Latch input, Latch output) {
            if (shutting_down) {
                addStatusWord("Shutting down");
                setActivity("");
                return;
            }
            
            input = input.duplicate();
            InstructionBase ins = input.getInstruction();
System.out.println("Instruction at decode :" +ins);
            // Default to no squashing.
            squashing_instruction = false;
            
            setActivity(ins.toString());

            IGlobals globals = (GlobalData)getCore().getGlobals();
            if (globals.getPropertyBoolean("decode_squash")) {
                // Drop the fall-through instruction.
                globals.setClockedProperty("decode_squash", false);
                squashing_instruction = false;
                //setActivity("----: NULL");
//                globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NULL);
                
                // Since we don't pass an instruction to the next stage,
                // must explicitly call input.consume in the case that
                // the next stage is busy.
                input.consume();
                return;
            }
            
            if (ins.isNull()) return;
            
            EnumOpcode opcode = ins.getOpcode();
            Operand oper0 = ins.getOper0();
            IRegFile regfile = globals.getRegisterFile();
            
            // This code is to prevent having more than one of the same regster
            // as a destiation register in the pipeline at the same time.
//            if (opcode.needsWriteback()) {
//                int oper0reg = oper0.getRegisterNumber();
//                if (regfile.isInvalid(oper0reg)) {
//                    //Logger.out.println("Stall because dest R" + oper0reg + " is invalid");
//                    setResourceWait("Dest:"+oper0.getRegisterName());
//                    return;
//                }
//            }
            if(ins.getSrc1().isRegister())
            {                
                ins.getSrc1().rename(GlobalData.rat[ins.getSrc1().getRegisterNumber()]);                        
            }
            if(ins.getSrc2().isRegister())
            {               
                ins.getSrc2().rename(GlobalData.rat[ins.getSrc2().getRegisterNumber()]);
            }
            if(ins.getOper0().isRegister())
            {
                if(opcode.oper0IsSource())
                {                                
                       ins.getOper0().rename(GlobalData.rat[ins.getOper0().getRegisterNumber()]);
                }
             }
            
            
            int x=0;
            
                for(int y=0;y<256;y++)
                {
                    if(!regfile.isUsed(y))
                    {
                        x=y;                    
                        break;
                    }
                }
            
            
            // See what operands can be fetched from the register file
            registerFileLookup(input);
            
            // See what operands can be fetched by forwarding
            forwardingSearch(input);
            
            Operand src1  = ins.getSrc1();
            Operand src2  = ins.getSrc2();
            
            
            boolean take_branch = false;
            int value0 = 0;
            int value1 = 0;
            
            
            // Find out whether or not DecodeToExecute can accept work.
            // We do this here for CALL, which can't be allowed to do anything
            // unless it can pass along its work to Writeback, and we pass
            // the call return address through Execute.
            int d2e_output_num = lookupOutput("DecodeToIssueQ");
            Latch d2e_output = this.newOutput(d2e_output_num);
            
            
            switch (opcode) {
                case BRA:
                    if (!oper0.hasValue()) {
                        // If we do not already have a value for the branch
                        // condition register, must stall.
//                        Logger.out.println("Stall BRA wants oper0 R" + oper0.getRegisterNumber());
                        this.setResourceWait(oper0.getRegisterName());
                        // Nothing else to do.  Bail out.
                        return;
                    }
                    value0 = oper0.getValue();
                    
                    // The CMP instruction just sets its destination to
                    // (src1-src2).  The result of that is in oper0 for the
                    // BRA instruction.  See comment in MyALU.java.
                    switch (ins.getComparison()) {
                        case EQ:
                            take_branch = (value0 == 0);
                            break;
                        case NE:
                            take_branch = (value0 != 0);
                            break;
                        case GT:
                            take_branch = (value0 > 0);
                            break;
                        case GE:
                            take_branch = (value0 >= 0);
                            break;
                        case LT:
                            take_branch = (value0 < 0);
                            break;
                        case LE:
                            take_branch = (value0 <= 0);
                            break;
                    }
                    
                    if (take_branch) {
                        // If the branch is taken, send a signal to Fetch
                        // that specifies the branch target address, via
                        // "globals.next_program_counter_takenbranch".  
                        // If the label is valid, then use its address.  
                        // Otherwise, the target address will be found in 
                        // src1.
                        if (ins.getLabelTarget().isNull()) {
                            // If branching to address in register, make sure
                            // operand is valid.
                            if (!src1.hasValue()) {
//                                Logger.out.println("Stall BRA wants src1 R" + src1.getRegisterNumber());
                                this.setResourceWait(src1.getRegisterName());
                                // Nothing else to do.  Bail out.
                                return;
                            }
                            
                            value1 = src1.getValue();
                        } else {
                            value1 = ins.getLabelTarget().getAddress();
                        }
                        globals.setClockedProperty("program_counter_takenbranch", value1);
                        
                        // Send a signal to Fetch, indicating that the branch
                        // is resolved taken.  This will be picked up by
                        // Fetch.advanceClock on the same clock cycle.
                        globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                        globals.setClockedProperty("decode_squash", true);
//                        Logger.out.println("Resolving branch taken");
                    } else {
                        // Send a signal to Fetch, indicating that the branch
                        // is resolved not taken.
                        globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NOT_TAKEN);
//                        Logger.out.println("Resolving branch not taken");
                    }
                    
                    // Since we don't pass an instruction to the next stage,
                    // must explicitly call input.consume in the case that
                    // the next stage is busy.
                    input.consume();
                    // All done; return.
                    return;
                    
                case JMP:
                    // JMP is an inconditionally taken branch.  If the
                    // label is valid, then take its address.  Otherwise
                    // its operand0 contains the target address.
                    if (ins.getLabelTarget().isNull()) {
                        if (!oper0.hasValue()) {
                            // If branching to address in register, make sure
                            // operand is valid.
//                            Logger.out.println("Stall JMP wants oper0 R" + oper0.getRegisterNumber());
                            this.setResourceWait(oper0.getRegisterName());
                            // Nothing else to do.  Bail out.
                            return;
                        }
                        
                        value0 = oper0.getValue();
                    } else {
                        value0 = ins.getLabelTarget().getAddress();
                    }
                    globals.setClockedProperty("program_counter_takenbranch", value0);
                    globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                    globals.setClockedProperty("decode_squash", true);
                    
                    // Since we don't pass an instruction to the next stage,
                    // must explicitly call input.consume in the case that
                    // the next stage is busy.
                    input.consume();
                    return;
                    
                case CALL:
                    // CALL is an inconditionally taken branch.  If the
                    // label is valid, then take its address.  Otherwise
                    // its src1 contains the target address.
                    if (ins.getLabelTarget().isNull()) {
                        if (!src1.hasValue()) {
                            // If branching to address in register, make sure
                            // operand is valid.
//                            Logger.out.println("Stall JMP wants oper0 R" + oper0.getRegisterNumber());
                            this.setResourceWait(src1.getRegisterName());
                            // Nothing else to do.  Bail out.
                            return;
                        }
                        
                        value1 = src1.getValue();
                    } else {
                        value1 = ins.getLabelTarget().getAddress();
                    }
                    
                    // CALL also has a destination register, which is oper0.
                    // Before we can resolve the branch, we have to make sure
                    // that the return address can be passed to Writeback
                    // through Execute before we go setting any globals.
                    if (!d2e_output.canAcceptWork()) return;
                    
                    // To get the return address into Writeback, we will
                    // replace the instruction's source operands with the
                    // address of the instruction and a constant 1.
                    
                    Operand pc_operand = Operand.newRegister(Operand.PC_REGNUM);
                    pc_operand.setIntValue(ins.getPCAddress());
                    ins.setSrc1(pc_operand);
                    ins.setSrc2(Operand.newLiteralSource(1));
                    ins.setLabelTarget(VoidLabelTarget.getVoidLabelTarget());
                    d2e_output.setInstruction(ins);
                    //regfile.markInvalid(oper0.getRegisterNumber());
                    
                    globals.setClockedProperty("program_counter_takenbranch", value1);
                    globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                    globals.setClockedProperty("decode_squash", true);
                    
                    // Do need to pass CALL to the next stage, so we do need
                    // to stall if the next stage can't accept work, so we
                    // do not explicitly consume the input here.  Since
                    // this code already fills the output latch, we can
                    // just quit. [hint for HW5]

                    output.copyAllPropertiesFrom(input);
                    d2e_output.write();
                    input.consume();
                    
                    return;
            }
            
            
            // Allocate an output latch for the output pipeline register
            // appropriate for the type of instruction being processed.
//            Latch output;
//            int output_num;           
//            if (opcode == EnumOpcode.MUL) 
//            {
//                output_num = lookupOutput("DecodeToMSFU");
//                output = this.newOutput(output_num);
//            }
//            else if (opcode == EnumOpcode.FMUL)
//            {
//                output_num = lookupOutput("DecodeToFMul");
//                output = this.newOutput(output_num);
//            }
//            else if (opcode == EnumOpcode.DIV) 
//            {
//                output_num = lookupOutput("DecodeToIDiv");
//                output = this.newOutput(output_num);
//            }
//            else if (opcode == EnumOpcode.FDIV) 
//            {
//                output_num = lookupOutput("DecodeToFDiv");
//                output = this.newOutput(output_num);
//            }
//            else if (opcode==EnumOpcode.FADD || opcode==EnumOpcode.FSUB || opcode==EnumOpcode.FCMP )
//            {
//                output_num=lookupOutput("DecodeToFAddSub");
//                output= this.newOutput(output_num);
//            }
//            else if (opcode.accessesMemory()) {
//                output_num = lookupOutput("DecodeToMemory");
//                output = this.newOutput(output_num);
//            }
//            else
//            {
//                output_num = lookupOutput("DecodeToExecute");
//                output = this.newOutput(output_num);
//            }
            
            // If the desired output is stalled, then just bail out.
            // No inputs have been claimed, so this will result in a
            // automatic pipeline stall.
//            if (!output.canAcceptWork()) return;
//            
//            
//            int[] srcRegs = new int[3];
//            // Only want to forward to oper0 if it's a source.
//            srcRegs[0] = opcode.oper0IsSource() ? oper0.getRegisterNumber() : -1;
//            srcRegs[1] = src1.getRegisterNumber();
//            srcRegs[2] = src2.getRegisterNumber();
//            Operand[] operArray = {oper0, src1, src2};
//            
//            // Loop over source operands, looking to see if any can be
//            // forwarded to the next stage.
//            for (int sn=0; sn<3; sn++) {
//                int srcRegNum = srcRegs[sn];
//                // Skip any operands that are not register sources
//                if (srcRegNum < 0) continue;
//                // Skip any that already have values
//                if (operArray[sn].hasValue()) continue;
//                
//                String propname = "forward" + sn;
//                if (!input.hasProperty(propname)) {
//                    // If any source operand is not available
//                    // now or on the next cycle, then stall.
//                    //Logger.out.println("Stall because no " + propname);
//                    this.setResourceWait(operArray[sn].getRegisterName());
//                    // Nothing else to do.  Bail out.
//                    return;
//                }
//            }
//            
//            if (ins.getOpcode() == EnumOpcode.HALT) shutting_down = true;            
//            
//            if (CpuSimulator.printForwarding) {
//                for (int sn=0; sn<3; sn++) {
//                    String propname = "forward" + sn;
//                    if (input.hasProperty(propname)) {
//                        String operName = PipelineStageBase.operNames[sn];
//                        String srcFoundIn = input.getPropertyString(propname);
//                        String srcRegName = operArray[sn].getRegisterName();
//                        Logger.out.printf("# Posting forward %s from %s to %s next stage\n", 
//                                srcRegName,
//                                srcFoundIn, operName);
//                    }
//                }
//            }            
                    
                        
                    
            // If we managed to find all source operands, mark the destination
            // register invalid then finish putting data into the output latch 
            // and send it.
            
            // Mark the destination register invalid
            if(opcode.needsWriteback())
            {
                regfile.markRenamed(GlobalData.rat[ins.getOper0().getRegisterNumber()], true);
                regfile.changeFlags(x,regfile.SET_USED|regfile.SET_INVALID,regfile.CLEAR_FLOAT|regfile.CLEAR_RENAMED );
                GlobalData.rat[ins.getOper0().getRegisterNumber()]=x;
                ins.getOper0().rename(x);
            }
//            if (opcode.needsWriteback()) {
//                int oper0reg = oper0.getRegisterNumber();
//                regfile.markInvalid(oper0reg);
//            }            

            
            // Copy the forward# properties
            output.copyAllPropertiesFrom(input);
            // Copy the instruction
            output.setInstruction(ins);
            // Send the latch data to the next stage
            output.write();
            
            // And don't forget to indicate that the input was consumed!
            input.consume();
        }
    }
    /*** Issue Queue ***/
    static class IssueQ extends PipelineStageBase
    {
        public IssueQ(CpuCore core) {
            super(core, "IssueQ");
        }
        
        public void compute()
            {
                Latch input=this.readInput(0).duplicate();
                InstructionBase ins = input.getInstruction();                             
                EnumOpcode opcode = ins.getOpcode();
                ICpuCore core = getCore();
                
                setActivity(ins.toString());
                
                if(ins.isNull()) return;
                int output_num;  
                boolean temp=false;
                String check_print=" ";
                for(int i=0;i<256;i++)
                {
                    if(GlobalData.i_queue[i]==null)
                    {
                        GlobalData.i_queue[i]=ins.getInstructionString();
                        GlobalData.i_latch[i]=input;
                        temp=true;
                        input.consume();
                        break;
                    }                                        
                }
//                for(int i=0;i<256;i++)
//                {
//                    if(GlobalData.i_queue[i]!=null )
//                    {
//                        System.out.println("afefwefw"+GlobalData.i_queue[i]);                        
//                    }                                        
//                }
                if(!temp)
                {
                    addStatusWord("IssueQueueFull");
                }
//                for(int i=0;i<GlobalData.i_queue.length;i++)
//                {
//                    if(GlobalData.i_queue[i]!=null && GlobalData.i_queue[i]!=" " )
//                    {
//                        check_print+=GlobalData.i_queue[i]+"/n";
//                    }                                        
//                }
                for(int i=0;i<GlobalData.i_queue.length;i++)
                {
                    if(GlobalData.i_queue[i]!=null && GlobalData.i_queue[i]!=" " )
                    {
                        ins=GlobalData.i_latch[i].getInstruction();
                                    
                    System.out.println("Instruction at IssueQ :" +ins);
                    
                    Latch output = null;
                    boolean flag_stall=false;
                    
                    if (opcode == EnumOpcode.MUL) 
                    {
                        output_num = lookupOutput("IssueQToMSFU");
                        output = this.newOutput(output_num);
                    }
                    else if (opcode == EnumOpcode.FMUL)
                    {
                        output_num = lookupOutput("IssueQToFMul");
                        output = this.newOutput(output_num);
                    }
                    else if (opcode == EnumOpcode.DIV) 
                    {
                        output_num = lookupOutput("IssueQToIDiv");
                        output = this.newOutput(output_num);
                    }
                    else if (opcode == EnumOpcode.FDIV) 
                    {
                        output_num = lookupOutput("IssueQToFDiv");
                        output = this.newOutput(output_num);
                    }
                    else if (opcode==EnumOpcode.FADD || opcode==EnumOpcode.FSUB || opcode==EnumOpcode.FCMP )
                    {
                        output_num=lookupOutput("IssueQToFAddSub");
                        output= this.newOutput(output_num);
                    }
                    else if (opcode.accessesMemory()) {
                        output_num = lookupOutput("IssueQToMemoryUnit");
                        output = this.newOutput(output_num);
                    }
                    else
                    {
                        output_num = lookupOutput("IssueQToExecute");
                        output = this.newOutput(output_num);
                    }
                                
                    forwardingSearch(GlobalData.i_latch[i]);

            
                
             boolean oper0src = opcode.oper0IsSource();

            Operand oper0 = ins.getOper0();
            Operand src1  = ins.getSrc1();
            Operand src2  = ins.getSrc2();
            // Put operands into array because we will loop over them,
            // searching the pipeline for forwarding opportunities.
            Operand[] operArray = {oper0, src1, src2};

    //            if(!output.canAcceptWork()) return;
               int[] srcRegs = new int[3];
            // Only want to forward to oper0 if it's a source.
            srcRegs[0] = oper0src ? oper0.getRegisterNumber() : -1;
            srcRegs[1] = src1.getRegisterNumber();
            srcRegs[2] = src2.getRegisterNumber(); 

            
            //boolean shutting_down=false;            
            
            // Loop over source operands, looking to see if any can be
            // forwarded to the next stage.
            for (int sn=0; sn<3; sn++) {
                int srcRegNum = srcRegs[sn];
                // Skip any operands that are not register sources
                if (srcRegNum < 0) continue;
                // Skip any that already have values
                if (operArray[sn].hasValue()) continue;
                
                String propname = "forward" + sn;
                if (!GlobalData.i_latch[i].hasProperty(propname)) {
                    // If any source operand is not available
                    // now or on the next cycle, then stall.
                    //Logger.out.println("Stall because no " + propname);
                    //this.setResourceWait(operArray[sn].getRegisterName());
                    // Nothing else to do.  Bail out.
                    flag_stall=true;
                    return;}
                
                
            }
             if(flag_stall)
             {
                 flag_stall=false;
                 continue;
             }
                                
            
            if (CpuSimulator.printForwarding) {
                for (int sn=0; sn<3; sn++) {
                    String propname = "forward" + sn;
                    if (GlobalData.i_latch[i].hasProperty(propname)) {
                        String operName = PipelineStageBase.operNames[sn];
                        String srcFoundIn = GlobalData.i_latch[i].getPropertyString(propname);
                        String srcRegName = operArray[sn].getRegisterName();
                        Logger.out.printf("# Posting forward %s from %s to %s next stage\n", 
                                srcRegName,
                                srcFoundIn, operName);
                    }
                }
            }
            if(!output.canAcceptWork()) return;
            output.copyAllPropertiesFrom(GlobalData.i_latch[i]);
            // Copy the instruction
            output.setInstruction(ins);
            // Send the latch data to the next stage
            output.write();
            GlobalData.i_queue[i]=null;
            GlobalData.i_latch[i]=null;
            // And don't forget to indicate that the input was consumed!
//            
         }   
      }             
    }
    }

    /*** Execute Stage ***/
    static class Execute extends PipelineStageBase {
        public Execute(ICpuCore core) {
            super(core, "Execute");
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) return;
            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();

            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().getValue();

            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
                        
            boolean isfloat = ins.getSrc1().isFloat() || ins.getSrc2().isFloat();
            output.setResultValue(result, isfloat);
            output.setInstruction(ins);
        }
    }
    
    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase {
        public Writeback(CpuCore core) {
            super(core, "Writeback");
        }
        
        boolean shutting_down = false;

        @Override
        public void compute() {
            List<String> doing = new ArrayList<String>();
            
            ICpuCore core = getCore();
            IGlobals globals = (GlobalData)core.getGlobals();
            // Get register file and valid flags from globals
            IRegFile regfile = globals.getRegisterFile();
            
            if (shutting_down) {
                Logger.out.println("disp=" + core.numDispatched() + " compl=" + core.numCompleted());
                setActivity("Shutting down");
            }
            if (shutting_down && core.numCompleted() >= core.numDispatched()) {
                globals.setProperty("running", false);
            }
            
            // Writeback has multiple inputs, so we just loop over them
            int num_inputs = this.getInputRegisters().size();
            for (int i=0; i<num_inputs; i++) {
                // Get the input by index and the instruction it contains
                Latch input = readInput(i);
                
                // Skip to the next iteration of there is no instruction.
                if (input.isNull()) continue;
                
                InstructionBase ins = input.getInstruction();
                if (ins.isValid()) core.incCompleted();
                doing.add(ins.toString());
                
                if (ins.getOpcode().needsWriteback()) {
                    // By definition, oper0 is a register and the destination.
                    // Get its register number;
                    Operand op = ins.getOper0();
                    String regname = op.getRegisterName();
                    int regnum = op.getRegisterNumber();
                    int value = input.getResultValue();
                    boolean isfloat = input.isResultFloat();

                    addStatusWord(regname + "=" + input.getResultValueAsString());
                    regfile.setValue(regnum, value, isfloat);
                }

                if (ins.getOpcode() == EnumOpcode.HALT) {
                    shutting_down = true;
                }
                
                // There are no outputs that could stall, so just consume
                // all valid inputs.
                input.consume();
            }
            
            setActivity(String.join("\n", doing));
        }
    }
    }
