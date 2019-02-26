/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package voidtypes;

import baseclasses.InstructionBase;
import tools.InstructionSequence;
import utilitytypes.IGlobals;
import utilitytypes.IRegFile;
import utilitytypes.RegisterFile;

/**
 *
 * @author millerti
 */
public class VoidGlobals extends VoidProperties implements IGlobals {
    private static final VoidGlobals singleton = new VoidGlobals();
    public static VoidGlobals getVoidGlobals() { return singleton; }
    protected VoidGlobals() {}

    @Override
    public void reset() {}

    @Override
    public void setup() {}

    @Override
    public InstructionBase getInstructionAt(int pc_address) {
        return VoidInstruction.getVoidInstruction();
    }

    @Override
    public void loadProgram(InstructionSequence seq) {}

    @Override
    public IRegFile getRegisterFile() {
        return VoidRegFile.getVoidRegFile();
    }

    @Override
    public IRegFile getPropertyRegisterFile(String name) {
        return VoidRegFile.getVoidRegFile();
    }

    @Override
    public InstructionBase[] getPropertyInstructionArr(String name) {
        return new InstructionBase[0];
    }

    @Override
    public void initClocking() { }

    @Override
    public void setClockedProperty(String name, Object val) {}

    @Override
    public void deleteClockedProperty(String name) {}

    @Override
    public void advanceClock() {}
}
