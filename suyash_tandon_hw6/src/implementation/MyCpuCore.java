/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import baseclasses.CpuCore;
import examples.MultiStageFunctionalUnit;
import java.util.Set;
import tools.InstructionSequence;
import utilitytypes.ClockedIntArray;
import utilitytypes.IClocked;
import utilitytypes.IGlobals;
import utilitytypes.IPipeReg;
import utilitytypes.IPipeStage;
import utilitytypes.IProperties;
import static utilitytypes.IProperties.*;
import utilitytypes.IRegFile;
import utilitytypes.Logger;
import voidtypes.VoidRegister;

/**
 * This is an example of a class that builds a specific CPU simulator out of
 * pipeline stages and pipeline registers.
 * 
 * @author 
 */
public class MyCpuCore extends CpuCore {
    static final String[] producer_props = {RESULT_VALUE};
        
    /**
     * Method that initializes the CpuCore.
     */
    @Override
    public void initProperties() {
        // Instantiate the CPU core's property container that we call "Globals".
        properties = new GlobalData();
        for(int a=0;a<32;a++)        
        {
            GlobalData.rat[a]=a;
            getGlobals().getRegisterFile().markUsed(a, true);
//          GlobalData.regfile.setIntValue(i, i);
//            GlobalData.regfile.markUsed(i, true);
            
        }

// Set all RAT entries to -1, mapping architectural register numbers to the ARF.
// Set all ARF entries as USED and VALID
    }
    
    public void loadProgram(InstructionSequence program) {
        getGlobals().loadProgram(program);
    }
    
    public void runProgram() {
        properties.setProperty(IProperties.CPU_RUN_STATE, IProperties.RUN_STATE_RUNNING);
        while (properties.getPropertyInteger(IProperties.CPU_RUN_STATE) != IProperties.RUN_STATE_HALTED) {
            Logger.out.println("## Cycle number: " + cycle_number);
            Logger.out.println("# State: " + getGlobals().getPropertyInteger(IProperties.CPU_RUN_STATE));
            IClocked.advanceClockAll();
        }
    }

    @Override
    public void createPipelineRegisters() {
        createPipeReg("FetchToDecode");
        createPipeReg("DecodeToExecute");
        createPipeReg("DecodeToMemory");
        
        createPipeReg("DecodeToMSFU");
        
        createPipeReg("DecodeToFAddSub");
        
        createPipeReg("DecodeToFMul");
        
        createPipeReg("DecodeToIDiv");
        createPipeReg("IDivToWriteback");
        
        createPipeReg("DecodeToFDiv");
        createPipeReg("FDivToWriteback");
        
//        createPipeReg("DecodeToMemoryUnit");
        createPipeReg("ExecuteToWriteback");
    }

    @Override
    public void createPipelineStages() {
        addPipeStage(new AllMyStages.Fetch(this));
        addPipeStage(new AllMyStages.Decode(this));
        addPipeStage(new AllMyStages.Execute(this));
//        addPipeStage(new AllMyStages.Memory(this));
//        addPipeStage(new AllMyStages.Writeback(this));
        addPipeStage(new IDiv(this));
        addPipeStage(new FDiv(this));
    }

    @Override
    public void createChildModules() {
        // MSFU is an example multistage functional unit.  Use this as a
        // basis for FMul, IMul, and FAddSub functional units.
        addChildUnit(new MultiStageFunctionalUnit(this, "MSFU"));
        addChildUnit(new FAddSub(this, "FAddSub"));
        addChildUnit(new FMul(this, "FMul"));
        addChildUnit(new MemoryUnit(this,"MemoryUnit"));
    }

    @Override
    public void createConnections() {
        
        // Connect pipeline elements by name.  Notice that 
        // Decode has multiple outputs, able to send to Memory, Execute,
        // or any other compute stages or functional units.
        // Writeback also has multiple inputs, able to receive from 
        // any of the compute units.
        // NOTE: Memory no longer connects to Execute.  It is now a fully 
        // independent functional unit, parallel to Execute.
        
        // Connect two stages through a pipelin register
        connect("Fetch", "FetchToDecode", "Decode");
        
        // Decode has multiple output registers, connecting to different
        // execute units.  
        // "MSFU" is an example multistage functional unit.  Those that
        // follow the convention of having a single input stage and single
        // output register can be connected simply my naming the functional
        // unit.  The input to MSFU is really called "MSFU.in".
        connect("Decode", "DecodeToExecute", "Execute");
        connect("Decode", "DecodeToMemory", "MemoryUnit");
        
        connect("Decode", "DecodeToMSFU", "MSFU");
        
        connect("Decode", "DecodeToFAddSub", "FAddSub");
        
//        connect("Decode", "DecodeToMemory", "MemoryUnit");
        
        connect("Decode", "DecodeToFMul", "FMul");
        
        connect("Decode", "DecodeToIDiv", "IDiv");
        connect("IDiv", "IDivToWriteback","Writeback");
        
        connect("Decode", "DecodeToFDiv", "FDiv");
        connect("FDiv", "FDivToWriteback","Writeback");
        
        // Writeback has multiple input connections from different execute
        // units.  The output from MSFU is really called "MSFU.Delay.out",
        // which was aliased to "MSFU.out" so that it would be automatically
        // identified as an output from MSFU.
        connect("Execute","ExecuteToWriteback", "Writeback");
//        connect("MemoryUnit", "MemoryToWriteback", "Writeback");
        
        connect("MSFU", "Writeback");
        
        connect("FAddSub", "Writeback");
        connect("MemoryUnit", "Writeback");
        
        connect("FMul", "Writeback");
//        connect("IDiv", "Writeback");
    }

    @Override
    public void specifyForwardingSources() {
        addForwardingSource("ExecuteToWriteback");
        //addForwardingSource("MemoryToWriteback");
        
        // MSFU.specifyForwardingSources is where this forwarding source is added
        // addForwardingSource("MSFU.out");
    }

    @Override
    public void specifyForwardingTargets() {
        // Not really used for anything yet
    }

    @Override
    public IPipeStage getFirstStage() {
        // CpuCore will sort stages into an optimal ordering.  This provides
        // the starting point.
        return getPipeStage("Fetch");
    }
    
    public MyCpuCore() {
        super(null, "core");
        initModule();
        printHierarchy();
        Logger.out.println("");
    }
}
