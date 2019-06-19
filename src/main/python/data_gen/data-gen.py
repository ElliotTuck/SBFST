#
# A script to generate train/dev/test set
#
import pynini
import functools
import numpy as np
import random

A = functools.partial(pynini.acceptor)
T = functools.partial(pynini.transducer)
e = pynini.epsilon_machine()

# Defining alphabet

alpha = "abcd"

zero = (e - e).optimize()
sigma = zero
for x in list(alpha):
    sigma = A(x) | sigma
sigma.optimize()


# Utility function that outputs all strings of an fsa

def list_string_set(acceptor):
    my_list = []
    paths = acceptor.paths()
    for s in paths.ostrings():
        my_list.append(s)
    my_list.sort(key=len)
    return my_list


def get_one_edit_distance_fsa(w):
    sigma_star = pynini.closure(sigma).optimize()
    sigma_plus = (sigma + sigma_star).optimize()

    ins1 = sigma_star + T("", "[I]") + sigma_star
    sub1 = sigma_star + T("", "[S]") + sigma_plus
    del1 = sigma_star + T("", "[D]") + sigma_plus

    edit1 = (ins1 | sub1 | del1).optimize()

    replaceI = zero
    for x in list(alpha):
        replaceI = replaceI | T("[I]", x)
    replaceI.optimize()

    replaceD = zero
    for x in list(alpha):
        replaceD = replaceD | T("[D]" + x, "")
    replaceD.optimize()

    replaceS = zero
    for x in list(alpha):
        for y in list(alpha):
            if x != y:
                replaceS = replaceS | T("[D]" + x, y)

    editTransducer = pynini.closure(sigma | replaceI | replaceD | replaceS)
    editTransducer.optimize()

    wBall = (w @ edit1 @ editTransducer).optimize()
    return wBall


# Utility function that gets the strings of an fsa
# with length from min_len to max_len

def get_pos_string(fsa, min_len, max_len):
    fsa_dict = {}
    for i in range(min_len, max_len + 1):
        fsa_dict[i] = pynini.intersect(fsa, pynini.closure(sigma, i, i))
        # print(list_string_set(fsa_dict[i]))
    return fsa_dict


# Utility function that gets the strings of the complement
# of an fsa with length from min_len to max_len

def get_neg_string(fsa, min_len, max_len):
    fsa_dict = {}
    for i in range(min_len, max_len + 1):
        fsa_dict[i] = pynini.difference(pynini.closure(sigma, i, i), fsa)
        # print(list_string_set(fsa_dict[i]))
    return fsa_dict


# Create {n} random strings from fsa.
# No duplicates in the results.
# The output fsa is the different between the original
# fsa and the delta fsa used to generate unique strings.


def rand_gen_no_duplicate(acceptor, n):
    loop = 10
    for i in range(loop):
        num = int(n + n*i*0.1)
        temp = pynini.randgen(acceptor, npath=num, seed=0, select="uniform", max_length=2147483647, weighted=False)
        rand_list = list_string_set(temp)
        rand_list = list(set(rand_list))
        uniq_len = len(rand_list)
        if uniq_len < n and i < loop - 1:
            print('insufficient random strings')
            continue
        else:
            random.shuffle(rand_list)
            rand_list = rand_list[:n]
            rand_list.sort()
            acceptor = pynini.difference(acceptor, temp)
            return acceptor, rand_list


# Create {num} positive and negative examples from fsa.
# No duplicates in the dataset.


def create_data_no_duplicate(filename, pos_dict, neg_dict, min_len, max_len, num):
    with open(filename, "w+") as f:
        for i in range(min_len, max_len + 1):
            acceptor, results = rand_gen_no_duplicate(pos_dict[i], num)
            pos_dict[i] = acceptor
            for ele in results:
                f.write(ele + "\t" + "TRUE\n")
            acceptor, results = rand_gen_no_duplicate(neg_dict[i], num)
            neg_dict[i] = acceptor
            for ele in results:
                f.write(ele + "\t" + "FALSE\n")
    return pos_dict, neg_dict


# create {num} random strings of positive/negative examples.
# This may be duplicates.


def create_data_with_duplicate(filename, pos_dict, neg_dict, min_len, max_len, num, get_difference):
    with open(filename, "w+") as f:
        for i in range(min_len, max_len + 1):
            pos_fsa = \
                pynini.randgen(pos_dict[i], npath=num, seed=0, select="uniform", max_length=2147483647, weighted=False)
            if get_difference == 1:
                pos_dict[i] = pynini.difference(pos_dict[i], pos_fsa)
            for ele in list_string_set(pos_fsa):
                f.write(ele + "\t" + "TRUE\n")
            neg_fsa = \
                pynini.randgen(neg_dict[i], npath=num, seed=0, select="uniform", max_length=2147483647, weighted=False)
            if get_difference == 1:
                neg_dict[i] = pynini.difference(neg_dict[i], neg_fsa)
            for ele in list_string_set(neg_fsa):
                f.write(ele + "\t" + "FALSE\n")
    return pos_dict, neg_dict


# create adversarial pairs

def create_adversarial_data(filename, pos_dict, neg_dict, min_len, max_len, num):
    with open(filename, "w+") as f:
        for i in range(min_len, max_len + 1):
            _, results = rand_gen_no_duplicate(pos_dict[i], num)
            for ele in results:
                one_edit_dist_fsa = get_one_edit_distance_fsa(A(ele))
                temp_fsa = pynini.compose(one_edit_dist_fsa, neg_dict[i])
                if i - 1 >= min_len:
                    temp_fsa = temp_fsa | pynini.compose(one_edit_dist_fsa, neg_dict[i - 1])
                if i + 1 <= max_len:
                    temp_fsa = temp_fsa | pynini.compose(one_edit_dist_fsa, neg_dict[i + 1])
#                print('one edit distance:' + ele)
#                print(list_string_set(temp_fsa))
#                temp_res = list(set(list_string_set(one_edit_dist_fsa)) & set(list_string_set(neg_dict[i])))
                temp_res = \
                    list_string_set(pynini.randgen(temp_fsa, npath=1, seed=0, select="uniform", max_length=2147483647, weighted=False))
                if not temp_res:
                    print('insufficient adversarial data')
                    continue
                else:
                    f.write(ele + "\t" + "TRUE\n")
                    f.write(temp_res[0] + "\t" + "FALSE\n")


# define FSA

# my_fsa = A("a").closure() | A("b").closure() | A("c").closure()
path_to_fsa = "pt3.fsa"
my_fsa = pynini.Fst.read(path_to_fsa)

# define hyper-parameters

ss_min_len = 10
ss_max_len = 19
train_pos_num = 50
dev1_pos_num = 50
test1_pos_num = 50
dev2_pos_num = 50
test2_pos_num = 50
ls_min_len = 31
ls_max_len = 50
test3_pos_num = 25
test4_pos_num = 25

dir_name = "data/PT3/1k/"

# generate short strings and construct a dictionary where
# key=length, value=a list of strings generated by fsa

pos_dict = get_pos_string(my_fsa, ss_min_len, ss_max_len)
neg_dict = get_neg_string(my_fsa, ss_min_len, ss_max_len)


# create training data with duplicates

pos_dict, neg_dict = \
    create_data_with_duplicate(dir_name + "Training.txt", pos_dict, neg_dict, ss_min_len, ss_max_len, train_pos_num, 1)


# create dev_1 and test_1 (with duplicates)
create_data_with_duplicate(dir_name + "dup_d.txt", pos_dict, neg_dict, ss_min_len, ss_max_len, dev1_pos_num, 0)
create_data_with_duplicate(dir_name + "dup_t.txt", pos_dict, neg_dict, ss_min_len, ss_max_len, test1_pos_num, 0)


# create dev_2 and test_2 (no duplicates, no overlap in train/dev/test data)
pos_dict, neg_dict = create_data_no_duplicate(dir_name + "Dev.txt", pos_dict, neg_dict, ss_min_len, ss_max_len, dev2_pos_num)
pos_dict, neg_dict = create_data_no_duplicate(dir_name + "Test1.txt", pos_dict, neg_dict, ss_min_len, ss_max_len, test2_pos_num)


# generate long strings

pos_dict = get_pos_string(my_fsa, ls_min_len, ls_max_len)
neg_dict = get_neg_string(my_fsa, ls_min_len, ls_max_len)


# create test_4 (adversarial examples)
create_adversarial_data(dir_name + "Test3.txt", pos_dict, neg_dict, ls_min_len, ls_max_len, test4_pos_num)


# create test_3 (no duplicates)
create_data_no_duplicate(dir_name + "Test2.txt", pos_dict, neg_dict, ls_min_len, ls_max_len, test3_pos_num)
