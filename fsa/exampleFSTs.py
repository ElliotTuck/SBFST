#
# A script to write out some simple FSAs
#

import pynini
import functools

A = functools.partial(pynini.acceptor) 
e = pynini.epsilon_machine()

# Defining sigma and sigmastar

sigma4 = e
for x in list("abcd"): sigma4 = A(x) | sigma4
sigma4.optimize()

sigma4Star = (sigma4.star).optimize()

a = A("a")
b = A("b")


def lg_containing_str(x,i):
    return (sigma4Star + pynini.closure(b,i,i) + sigma4Star).minimize()

def lg_containing_ssq(x,i):
    return (pynini.closure(sigma4Star + x + sigma4Star,i,i)).minimize()



###############
# SL Examples #
###############

sl = dict()
sl[0] = sigma4Star - lg_containing_str(b,2)  # SL2 , forbidden bb
sl[1] = sigma4Star - lg_containing_str(b,4)  # SL4 , forbidden bbbb
sl[2] = sigma4Star - lg_containing_str(b,8)  # SL8 , forbidden bbbbbbbb

###############
# SP Examples #
###############

sp=dict()
sp[0] = sigma4Star - lg_containing_ssq(b,2)     # SP2 , forbidden bb
sp[1] = sigma4Star - lg_containing_ssq(b,4)     # SP4 , forbidden bbbb
sp[2] = sigma4Star - lg_containing_ssq(b,8)     # SP8 , forbidden bbbbbbbb

###############
# LT Examples #
###############

lt=dict()
# LT2 , at least one bb
lt[0] = lg_containing_str(b,2)

# LT4 , at least one bbbb or at least one aaaa
lt[1] = pynini.union(lg_containing_str(b,4), lg_containing_str(a,4))
# lt[1] = lg_containing_str(b,4) + lg_containing_str(a,4)

# LT4 , at least one bbbb and at least one aaaa
lt[2] = pynini.intersect(lg_containing_str(b,4), lg_containing_str(a,4))
        
# LT8 , if b^8 then a^8 (~~~ not b^8 or a^8)
lt[3] = (sigma4Star - lg_containing_str(b,8)) + lg_containing_str(a,8)

###############
# PT Examples #
###############

pt=dict()
# PT2 , at least one bb
pt[0] = lg_containing_ssq(b,2)

# PT4 , at least one bbbb or at least one aaaa
pt[1] = pynini.union(lg_containing_ssq(b,4), lg_containing_ssq(a,4))
# pt[1] = lg_containing_ssq(b,4) + lg_containing_ssq(a,4)

# PT4 , at least one bbbb and at least one aaaa
pt[2] = pynini.intersect(lg_containing_ssq(b,4), lg_containing_ssq(a,4))
        
# PT8 , if b^8 then a^8 (~~~ not b^8 or a^8)
pt[3] = (sigma4Star - lg_containing_ssq(b,8)) + lg_containing_ssq(a,8)


################
# LTT Examples #
################
ltt=dict()

######################
# Star-Free Examples #
######################
sf=dict()

####################
# Regular Examples #
####################
reg=dict()


pair_names=[(sl,'sl'),
            (sp,'sp'),
            (lt,'lt'),
            (pt,'pt'),
            (ltt,'ltt'),
            (sf,'sf'),
            (reg,'reg')]

lg_classes = dict(enumerate(pair_names))

# Minimizing the acceptors
#          and
# Writing them to files

for x in lg_classes:
    lg_class,name = lg_classes[x]
    for i in list(range(len(lg_class))):
        lg_class[i].optimize()
        filename = name+str(i)
        # lg_class[i].write(filename+".fsa")
        with open('../src/test/resources/' + filename + '.fst.txt', 'w') as f_out:
            f_out.write(str(lg_class[i]))
        with open('../src/test/resources/' + filename + '.states.syms', 'w') as f_out:
            for i in range(lg_class[i].num_states()):
                f_out.write(str(i) + '\t' + str(i) + '\n')
        with open('../src/test/resources/' + filename + '.input.syms', 'w') as f_out:
            f_out.write('a\t0\nb\t1\nc\t2\nd\t3')
        with open('../src/test/resources/' + filename + '.output.syms', 'w') as f_out:
            f_out.write('a\t0\nb\t1\nc\t2\nd\t3')
        # print(filename)
        # print(lg_class[i])
        # include only if you want to see a drawing of the fsa
        # a.draw(filename+".dot",title=filename,acceptor=True)

        
# from the command line, try
# fstprint a1.fst
# etc
# 
