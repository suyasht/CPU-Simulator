/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import utilitytypes.IGlobals;
import tools.InstructionSequence;

/**
 * As a design choice, some data elements that are accessed by multiple
 * pipeline stages are stored in a common object.
 * 
 * TODO:  Add to this any additional global or shared state that is missing.
 * 
 * @author 
 */
public class GlobalData implements IGlobals {
    public InstructionSequence program;
    public int program_counter = 0;
    public int[] register_file = new int[32];
    public boolean[] register_invalid = new boolean[32];
    public int[] memory_space= new int[1000];
    public int temp=1;
    public boolean branch_flag=false;
    public boolean check_stall=false;
    public boolean isbranch=true;
    public boolean branch=false;      
    public boolean isbranch_fetch=false;
    public int index=0;
    public int result_memory;
    public int result_execute;
    public int result_writeback;
    public int resultin_memory;
    public int resultin_execute;
    public int resultin_writeback;
    public boolean check_stall_src1=false;
    public boolean check_stall_src2=false;
    public boolean forwardingflag=false;

    @Override
    public void reset() {
        program_counter = 0;
        register_file = new int[32];
    }
    
    
    // Other global and shared variables here....

}
