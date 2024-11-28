package rars.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import rars.*;
import rars.api.Options;
import rars.api.Program;
import rars.riscv.*;
import rars.simulator.Simulator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class RarsTest {
    @BeforeAll
    public static void testMain(){
        Globals.initialize();
    }

    private static Stream<Arguments> provideRiscvTests() {
        Options opt = new Options();
        opt.startAtMain = true;
        opt.maxSteps = 1000;
        Program prog = new Program(opt);
        return Stream.of(
                ".",
            "riscv-tests",
            "riscv-tests-64"
        ).map(Path.of("src/test/resources")::resolve)
                .flatMap(s -> {
                    try {
                        return Files.list(s);
                    } catch (IOException e) {
                        fail("Could not list files in " + s, e);
                        return null;
                    }
                })
                .filter(Files::isRegularFile)
                .filter(s -> s.getFileName().toString().endsWith(".s"))
                .map(p -> Arguments.of(
                        p,
                        p.getParent().getFileName().toString().contains("64"),
                        prog
                ));
    }

    @ParameterizedTest
    @MethodSource("provideRiscvTests")
    void run(Path path, boolean rv64, Program p) {
        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.RV64_ENABLED, rv64);
        InstructionSet.rv64 = rv64;
        Globals.instructionSet.populate();

        int[] errorlines = null;
        String stdin = "", stdout = "", stderr ="";
        // TODO: better config system
        // This is just a temporary solution that should work for the tests I want to write
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line = br.readLine();
            while(line.startsWith("#")){
                if (line.startsWith("#error on lines:")) {
                    String[] linenumbers = line.replaceFirst("#error on lines:", "").split(",");
                    errorlines = new int[linenumbers.length];
                    for(int i = 0; i < linenumbers.length; i++){
                        errorlines[i] = Integer.parseInt(linenumbers[i].trim());
                    }
                } else if (line.startsWith("stdin:")) {
                    stdin = line.replaceFirst("#stdin:", "").replaceAll("\\\\n","\n");
                } else if (line.startsWith("#stdout:")) {
                    stdout = line.replaceFirst("#stdout:", "").replaceAll("\\\\n","\n");
                } else if (line.startsWith("#stderr:")) {
                    stderr = line.replaceFirst("#stderr:", "").replaceAll("\\\\n","\n");
                }
                line = br.readLine();
            }
        }catch(FileNotFoundException fe){
            fail("Could not find " + path, fe);
        }catch(IOException io){
            fail("Error reading " + path, io);
        }
        try {
            p.assemble(path.toAbsolutePath().normalize().toString());
            if(errorlines != null){
                fail("Expected asssembly error, but successfully assembled " + path);
            }
            p.setup(null,stdin);
            Simulator.Reason r = p.simulate();
            if(r != Simulator.Reason.NORMAL_TERMINATION){
                fail("Ended abnormally while executing " + path);
            }else{
                if(p.getExitCode() != 42) {
                    fail("Final exit code was wrong for " + path);
                }
                if(!p.getSTDOUT().equals(stdout)){
                    fail("STDOUT was wrong for " + path + "\n Expected \""+stdout+"\" got \""+p.getSTDOUT()+"\"");
                }
                if(!p.getSTDERR().equals(stderr)){
                    fail("STDERR was wrong for " + path);
                }
            }
        } catch (AssemblyException ae){
            if(errorlines == null) {
                fail("Failed to assemble " + path);
            }
            if(ae.errors().errorCount() != errorlines.length){
                fail("Mismatched number of assembly errors in" + path);;
            }
            Iterator<ErrorMessage> errors = ae.errors().getErrorMessages().iterator();
            for(int number : errorlines){
                ErrorMessage error = errors.next();
                while(error.isWarning()) error = errors.next();
                if(error.getLine() != number){
                    fail("Expected error on line " + number + ". Found error on line " + error.getLine()+" in " + path);
                }
            }
        } catch (SimulationException se){
            fail("Crashed while executing " + path, se);
        }
    }

    @Test
    public void checkBinary(){
        Options opt = new Options();
        opt.startAtMain = true;
        opt.maxSteps = 500;
        opt.selfModifyingCode = true;
        Program p = new Program(opt);
        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.SELF_MODIFYING_CODE_ENABLED, true);

        ArrayList<Instruction> insts = Globals.instructionSet.getInstructionList();
        for(Instruction inst : insts){
            if(inst instanceof BasicInstruction){
                BasicInstruction binst = (BasicInstruction) inst;
                if(binst.getInstructionFormat() == BasicInstructionFormat.B_FORMAT ||
                        binst.getInstructionFormat() == BasicInstructionFormat.J_FORMAT)
                    continue;

                String program = inst.getExampleFormat();
                try {
                    p.assembleString(program);
                    p.setup(null,"");
                    int word = p.getMemory().getWord(0x400000);
                    ProgramStatement assembled = p.getMemory().getStatement(0x400000);
                    ProgramStatement ps = new ProgramStatement(word,0x400000);
                    if (ps.getInstruction() == null) {
                        fail("Error 1 on: " + program);
                    }
                    if (ps.getPrintableBasicAssemblyStatement().contains("invalid")) {
                        fail("Error 2 on: " + program);
                    }
                    String decompiled = ps.getPrintableBasicAssemblyStatement();

                    p.assembleString(program);
                    p.setup(null,"");
                    int word2 = p.getMemory().getWord(0x400000);
                    if (word != word2) {
                        fail("Error 3 on: " + program);
                    }


                    if(!ps.getInstruction().equals(binst)){
                        fail("Error 4 on: " + program);
                    }

/*
                    if (assembled.getInstruction() == null) {
                        fail("Error 5 on: " + program);
                        continue;
                    }
                    if (assembled.getOperands().length != ps.getOperands().length){
                        fail("Error 6 on: " + program);
                        continue;
                    }
                    for (int i = 0; i < assembled.getOperands().length; i++){
                        if(assembled.getOperand(i) != ps.getOperand(i)){
                            fail("Error 7 on: " + program);
                        }
                    }*/

                    /*
                    // Not currently used
                    // Do a bit of trial and error to test out variations
                    decompiled = decompiled.replaceAll("x6","t1").replaceAll("x7","t2").replaceAll("x28","t3").trim();
                    String spaced_out = decompiled.replaceAll(",",", ");
                    if(!program.equals(decompiled) && !program.equals(spaced_out)){
                        Globals.getSettings().setBooleanSetting(Settings.Bool.DISPLAY_VALUES_IN_HEX,false);
                        decompiled = ps.getPrintableBasicAssemblyStatement();
                        String decompiled2 = decompiled.replaceAll("x6","t1").replaceAll("x7","t2").replaceAll("x28","t3").trim();
                        String spaced_out2 = decompiled2.replaceAll(",",", ");
                        if(!program.equals(decompiled2) && !program.equals(spaced_out2)) {
                            fail("Error 5 on: " + program);
                        }

                        Globals.getSettings().setBooleanSetting(Settings.Bool.DISPLAY_VALUES_IN_HEX,true);
                    }
                    */
                } catch (Exception e) {
                    fail("Error 5 on: " + program, e);
                }
            }
        }
    }

    @Test
    public void checkPsuedo(){
        Options opt = new Options();
        opt.startAtMain = true;
        opt.maxSteps = 500;
        opt.selfModifyingCode = true;
        Program p = new Program(opt);
        Globals.getSettings().setBooleanSettingNonPersistent(Settings.Bool.SELF_MODIFYING_CODE_ENABLED, true);

        ArrayList<Instruction> insts = Globals.instructionSet.getInstructionList();
        int skips = 0;
        for(Instruction inst : insts){
            if(inst instanceof ExtendedInstruction){
                String program = "label:"+inst.getExampleFormat();
                try {
                    p.assembleString(program);
                    p.setup(null,"");
                    int first = p.getMemory().getWord(0x400000);
                    int second = p.getMemory().getWord(0x400004);
                    ProgramStatement ps = new ProgramStatement(first,0x400000);
                    if (ps.getInstruction() == null) {
                        fail("Error 11 on: " + program);
                    }
                    if (ps.getPrintableBasicAssemblyStatement().contains("invalid")) {
                        fail("Error 12 on: " + program);
                    }
                    if(program.contains("t0") || program.contains("t1") ||program.contains("t2") ||program.contains("f1")) {
                        // TODO: test that each register individually is meaningful and test every register.
                        // Currently this covers all instructions and is an alert if I made a trivial mistake.
                        String register_substitute = program.replaceAll("t0", "x0").replaceAll("t1", "x0").replaceAll("t2", "x0").replaceAll("f1", "f0");
                        p.assembleString(register_substitute);
                        p.setup(null, "");
                        int word1 = p.getMemory().getWord(0x400000);
                        int word2 = p.getMemory().getWord(0x400004);
                        if (word1 == first && word2 == second) {
                            fail("Error 13 on: " + program);
                        }
                    }else{
                        skips++;
                    }
                } catch (Exception e) {
                    fail("Error 14 on: " + program, e);
                }
            }
        }
        // 12 was the value when this test was written, if instructions are added that intentionally
        // don't have those registers in them add to the register list above or add to the count.
        // Updated to 10: because fsrmi and fsflagsi were removed
        if(skips != 10) fail("Unexpected number of psuedo-instructions skipped.");
    }
}
